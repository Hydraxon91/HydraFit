/** Model-tool boundary only. Direct user/API execution and hostile plugins are out of scope. */
import { execFile } from 'node:child_process';
import { createHash } from 'node:crypto';
import { readFile, realpath } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const SUPPORTED = new Set(['read', 'glob', 'grep']);
const bridge = fileURLToPath(new URL('../task-guard-state.py', import.meta.url));

export class GuardError extends Error {}

export function stateRequest(request, python = 'python3') {
  return new Promise((resolve, reject) => {
    const child = execFile(python, ['-B', bridge], { timeout: 30000, maxBuffer: 65536 }, (error, stdout) => {
      let response;
      try { response = JSON.parse(stdout); }
      catch { reject(new GuardError('state bridge unavailable; outcome may be unknown')); return; }
      if (error || response.ok !== true) {
        reject(new GuardError(response.reason || 'state update uncertain; inspect before retry'));
      } else { resolve(response); }
    });
    child.stdin.on('error', () => {}); // Process failure is reported through execFile's callback.
    child.stdin.end(JSON.stringify(request));
  });
}

export function operationID(sessionID, callID) {
  return `guard-${createHash('sha256').update(`${sessionID}\0${callID}`).digest('hex')}`;
}

export function filterTools(tools) {
  for (const name of Object.keys(tools)) {
    if (!SUPPORTED.has(name)) delete tools[name];
  }
}

function validBinding(binding) {
  if (!binding || typeof binding !== 'object' || Array.isArray(binding) ||
      !path.isAbsolute(binding.repo || '') || !path.isAbsolute(binding.stateDir || '') ||
      typeof binding.task !== 'string' || !/^[A-Za-z0-9][A-Za-z0-9._-]{0,99}$/.test(binding.task)) {
    throw new GuardError('invalid task binding');
  }
  return binding;
}

export function makeGuard({ bindingsFile, getSession, repository, request = stateRequest, python = 'python3' }) {
  const active = new Map();

  async function bindingFor(sessionID) {
    if (!path.isAbsolute(bindingsFile || '') || typeof sessionID !== 'string' || !sessionID) {
      throw new GuardError('explicit bindingsFile and session identity required');
    }
    let bindings;
    try { bindings = JSON.parse(await readFile(bindingsFile, 'utf8')); }
    catch { throw new GuardError('bindings unavailable; refusing model tools'); }
    if (bindings.version !== 1 || !bindings.sessions || typeof bindings.sessions !== 'object' || Array.isArray(bindings.sessions)) {
      throw new GuardError('invalid bindings file');
    }
    const visited = new Set();
    for (let depth = 0; depth < 32; depth++) {
      if (visited.has(sessionID)) throw new GuardError('session ancestry cycle');
      visited.add(sessionID);
      if (Object.hasOwn(bindings.sessions, sessionID)) {
        const binding = validBinding(bindings.sessions[sessionID]);
        if (repository && await realpath(repository) !== await realpath(binding.repo)) {
          throw new GuardError('binding belongs to a different plugin location');
        }
        return binding;
      }
      const session = await getSession(sessionID);
      if (!session || session.id !== sessionID) throw new GuardError('cannot establish session ancestry');
      if (!session.parentID) return null;
      sessionID = session.parentID;
    }
    throw new GuardError('session ancestry too deep');
  }

  const keyFor = binding => JSON.stringify([binding.repo, binding.stateDir, binding.task]);
  const send = (binding, action, extras = {}) => request({ ...binding, action, ...extras }, python);

  async function permission(event) {
    try {
      const binding = await bindingFor(event.sessionID);
      if (!binding) return;
      const activeCall = active.get(keyFor(binding));
      if (!activeCall || event.source?.id !== activeCall.callID || event.sessionID !== activeCall.sessionID) {
        throw new GuardError('permission check has no admitted executor');
      }
      if (!['read', 'glob', 'grep', 'external_directory'].includes(event.action)) {
        throw new GuardError('permission action unsupported by pilot');
      }
      if (event.action === 'read' || event.action === 'external_directory') {
        const root = await realpath(binding.repo);
        for (const resource of event.resources) {
          if (typeof resource !== 'string') throw new GuardError('invalid permission resource');
          const target = resource.endsWith('/*') ? resource.slice(0, -2) : resource;
          const resolved = await realpath(path.resolve(root, target));
          if (resolved !== root && !resolved.startsWith(root + path.sep)) throw new GuardError('read outside bound repository');
        }
      }
      await send(binding, 'inspect', { operation: activeCall.operation });
      // Never upgrade allow/ask or override a configured deny.
    } catch (error) {
      event.effect = 'deny';
      event.message = `Task guard: ${error.message}`;
    }
  }

  function wrap(tool, execute) {
    return async (input, context) => {
      const binding = await bindingFor(context.sessionID);
      if (!binding) return execute(input, context);
      if (!SUPPORTED.has(tool)) throw new GuardError(`unsupported model tool: ${tool}`);
      if (!input || typeof input !== 'object' || Array.isArray(input)) throw new GuardError('invalid tool input');
      const root = await realpath(binding.repo);
      const target = input.path ?? (tool === 'read' ? null : root);
      if (typeof target !== 'string' || !target) throw new GuardError('tool requires a repository path');
      const resolved = await realpath(path.resolve(root, target));
      if (resolved !== root && !resolved.startsWith(root + path.sep)) throw new GuardError('tool path outside bound repository');
      // Native executors may perform their permission checks outside this wrapper.
      // Validate here too, and pin the path rather than forwarding a symlink alias.
      const guardedInput = { ...input, path: resolved };
      if (typeof context.id !== 'string' || !context.id || context.signal?.aborted) {
        throw new GuardError('tool call identity missing or already interrupted');
      }
      const key = keyFor(binding);
      if (active.has(key)) throw new GuardError('task already has an admitted executor');
      const admission = { sessionID: context.sessionID, callID: context.id,
        operation: operationID(context.sessionID, context.id) };
      // Reserve in memory before the first await; the Python lock covers other processes.
      active.set(key, admission);
      try {
        await send(binding, 'begin', { operation: admission.operation, tool });
        if (context.signal?.aborted) throw new GuardError('interrupted after admission; outcome unknown');
        const result = await execute(guardedInput, context);
        if (context.signal?.aborted) throw new GuardError('interrupted executor; outcome unknown');
        await send(binding, 'complete', { operation: admission.operation });
        return result;
      } finally {
        // Errors/interruption never manufacture a failed/not-performed resolution.
        active.delete(key);
      }
    };
  }
  return { wrap, permission, bindingFor };
}
