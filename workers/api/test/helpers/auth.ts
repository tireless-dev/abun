import type {
  AuthServiceLike,
  AuthSessionResult,
  AuthenticatedSession,
  LogoutRequest,
} from "../../src/services/auth-service";

export function createPassThroughAuthEnv(): never {
  return {
    ABUN_REQUIRE_AUTH: "true",
    __authService: createPassThroughAuthService(),
  } as never;
}

function createPassThroughAuthService(): AuthServiceLike {
  return {
    requestAuth(): void {
      throw new Error("requestAuth is not supported by the pass-through auth test helper");
    },
    verifyAuth(): AuthSessionResult {
      throw new Error("verifyAuth is not supported by the pass-through auth test helper");
    },
    refreshSession(): AuthSessionResult {
      throw new Error("refreshSession is not supported by the pass-through auth test helper");
    },
    logoutSession(_request: LogoutRequest): void {
    },
    authenticateAccessToken(accessToken: string): AuthenticatedSession {
      return {
        sessionId: `test-session:${accessToken}`,
        userId: accessToken,
      };
    },
  };
}
