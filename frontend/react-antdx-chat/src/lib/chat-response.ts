/** Preserve backend error messages before the SDK replaces them with an HTTP status. */
export async function checkChatResponse(response: Response): Promise<Response> {
  if (response.ok) return response;

  let message = `聊天请求失败（HTTP ${response.status}）`;
  try {
    const body = await response.clone().json();
    const detail = body?.message ?? body?.error?.message ?? body?.error;
    if (typeof detail === 'string' && detail.trim()) message = detail;
  } catch {
    // Empty bodies and proxy HTML errors still need a useful status message.
  }
  throw new Error(message);
}
