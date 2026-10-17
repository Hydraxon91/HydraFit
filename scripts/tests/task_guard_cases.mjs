/** Offline wrapper contracts. No server/model invocation; Python tests cover the state bridge. */
import assert from 'node:assert/strict';
import { mkdtemp, writeFile, rm, symlink } from 'node:fs/promises';
import os from 'node:os';
import path from 'node:path';
import { filterTools, makeGuard, operationID } from '../opencode-task-guard/guard.mjs';

const root = await mkdtemp(path.join(os.tmpdir(), 'task-guard-'));
const bindingsFile = path.join(root, 'bindings.json');
const binding = { repo: root, stateDir: path.join(root, 'state'), task: 'task' };
const context = id => ({ sessionID: 'parent', id, signal: new AbortController().signal });
let pending = null;
const seen = new Set();
const events = [];
let blocked = false;
async function request(event) {
  events.push(event.action);
  if (blocked && event.action !== 'complete') throw new Error('state unavailable or expired');
  if (event.action === 'begin') {
    assert.equal(pending, null);
    if (seen.has(event.operation)) throw new Error('duplicate operation');
    pending = event.operation;
    seen.add(pending);
  } else if (event.action === 'complete') {
    assert.equal(event.operation, pending);
    pending = null;
  } else { assert.equal(event.operation, pending); }
  return { ok: true };
}
const getSession = async id => ({ id, parentID: id === 'child' ? 'parent' : null });
try {
  await writeFile(bindingsFile, JSON.stringify({ version: 1, sessions: { parent: binding } }));
  const guard = makeGuard({ bindingsFile, getSession, request });
  let executions = 0;
  const execute = async () => { executions++; return 'observed result'; };
  assert.equal(await guard.wrap('read', execute)({path:root}, context('first')), 'observed result');
  assert.equal(executions, 1);
  assert.deepEqual(events, ['begin', 'complete']);
  assert.equal(pending, null);
  await assert.rejects(guard.wrap('read', execute)({path:root}, context('first')), /duplicate/);
  assert.equal(executions, 1);
  for (const name of ['shell', 'edit', 'patch', 'execute', 'maestro_run', 'unknown']) {
    await assert.rejects(guard.wrap(name, execute)({}, context(name)), /unsupported/);
  }
  assert.equal(executions, 1);
  blocked = true;
  await assert.rejects(guard.wrap('read', execute)({path:root}, context('expired')), /state unavailable/);
  assert.equal(executions, 1);
  blocked = false;
  const child = { ...context('child-call'), sessionID: 'child' };
  assert.equal(await guard.wrap('read', execute)({path:root}, child), 'observed result');
  assert.deepEqual(await guard.bindingFor('child'), binding);
  assert.equal(await guard.wrap('shell', execute)({}, { ...context('unbound'), sessionID: 'unbound' }), 'observed result');
  let finish;
  const longRead = guard.wrap('read', () => new Promise(resolve => { finish = resolve; }));
  const running = longRead({path:root}, context('running'));
  while (!finish) await new Promise(resolve => setImmediate(resolve));
  await assert.rejects(guard.wrap('read', execute)({path:root}, child), /already has/);
  const allowed = { sessionID: 'parent', source: { id: 'running' }, action: 'read', resources: [root], effect: 'ask' };
  await guard.permission(allowed);
  assert.equal(allowed.effect, 'ask'); // A guard must never auto-approve.
  const unrelated = { ...allowed, source: { id: 'other' }, effect: 'allow' };
  await guard.permission(unrelated);
  assert.equal(unrelated.effect, 'deny');
  const outside = { ...allowed, resources: [os.tmpdir()], effect: 'allow' };
  await guard.permission(outside);
  assert.equal(outside.effect, 'deny');
  finish('done');
  await running;
  const advertised={read:{},glob:{},grep:{},execute:{},shell:{},maestro_run:{}};
  filterTools(advertised);
  assert.deepEqual(Object.keys(advertised),['read','glob','grep']);
  await assert.rejects(guard.wrap('read', execute)({path:os.tmpdir()}, context('escape')), /outside bound/);
  const alias=path.join(root,'outside');
  await symlink(os.tmpdir(),alias);
  await assert.rejects(guard.wrap('read', execute)({path:alias}, context('symlink')), /outside bound/);
  await assert.rejects(guard.wrap('read', async () => { throw new Error('executor failed'); })({path:root}, context('failure')), /executor failed/);
  assert.equal(pending, operationID('parent', 'failure')); // No fabricated resolution.
  pending = null; // Fake bridge reset only; real recovery is covered by Python tests.
  const controller = new AbortController();
  await assert.rejects(guard.wrap('read', async () => { controller.abort(); return 'late'; })({path:root},
    { ...context('aborted'), signal: controller.signal }), /outcome unknown/);
  assert.equal(pending, operationID('parent', 'aborted'));
  await writeFile(bindingsFile, 'broken');
  await assert.rejects(guard.wrap('shell', execute)({}, { ...context('unbound'), sessionID: 'unbound' }), /bindings unavailable/);
  const denied = { ...allowed, effect: 'allow' };
  await guard.permission(denied);
  assert.equal(denied.effect, 'deny');
  console.log('wrapper contracts passed (mock bridge; not a model-loop test)');
} finally {
  await rm(root, { recursive: true, force: true });
}
