export interface ErrorResponse {
  status: number;
  error: string;
  code: string;
  message: string;
  path: string;
  timestamp: string;
  fieldErrors?: Record<string, string>;
}
