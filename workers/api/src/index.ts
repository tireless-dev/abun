import type { WorkerEnv } from "./env";
import { toErrorResponse } from "./http/errors";
import { routeRequest } from "./http/router";

export default {
  async fetch(request, env) {
    try {
      return await routeRequest(request, env);
    } catch (error) {
      return toErrorResponse(error);
    }
  },
} satisfies ExportedHandler<WorkerEnv>;
