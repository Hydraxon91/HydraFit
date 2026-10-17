/** Explicit opt-in pilot. Loading this directory does not activate it elsewhere. */
import { filterTools, makeGuard } from './guard.mjs';

export default {
  id: 'hydrafit.task-guard-pilot',
  async setup(ctx) {
    const guard = makeGuard({ bindingsFile: ctx.options.bindingsFile,
      python: ctx.options.python || 'python3',
      repository: ctx.location.directory,
      getSession: sessionID => ctx.session.get({ sessionID }) });
    await ctx.permission.hook('evaluate', guard.permission);
    await ctx.session.hook('context', async event => {
      try {
        if (await guard.bindingFor(event.sessionID)) filterTools(event.tools);
      } catch {
        // Invalid/missing bindings cannot silently turn a bound session into unbound.
        for (const name of Object.keys(event.tools)) delete event.tools[name];
      }
    });
    await ctx.tool.transform(editor => {
      for (const tool of editor.list()) {
        if (typeof tool.execute !== 'function') continue;
        const execute = tool.execute;
        editor.update(tool.id, draft => { draft.execute = guard.wrap(tool.id, execute); });
      }
    });
  }
};
