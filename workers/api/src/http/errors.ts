import { json } from "./json";

export class HttpError extends Error {
  readonly status: number;

  constructor(
    status: number,
    message: string,
  ) {
    super(message);
    this.name = "HttpError";
    this.status = status;
  }
}

export function isHttpError(error: unknown): error is HttpError {
  return error instanceof HttpError;
}

export function toErrorResponse(error: unknown): Response {
  if (isHttpError(error)) {
    return json(
      { message: error.message },
      { status: error.status },
    );
  }

  return json(
    { message: "Internal server error" },
    { status: 500 },
  );
}
