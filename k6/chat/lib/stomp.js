/**
 * STOMP 1.2 프레임 빌더 / 파서
 * raw WebSocket 위에서 STOMP 프로토콜을 직접 구현한다.
 */

function buildFrame(command, headers = {}, body = '') {
  let frame = command + '\n';
  for (const [k, v] of Object.entries(headers)) {
    frame += `${k}:${v}\n`;
  }
  frame += '\n' + body + '\0';
  return frame;
}

export const stomp = {
  connect(token) {
    return buildFrame('CONNECT', {
      'accept-version': '1.2',
      'heart-beat': '0,0',
      Authorization: `Bearer ${token}`,
    });
  },

  subscribe(id, destination) {
    return buildFrame('SUBSCRIBE', { id, destination });
  },

  send(destination, bodyObj) {
    const body = JSON.stringify(bodyObj);
    return buildFrame('SEND', {
      destination,
      'content-type': 'application/json',
    }, body);
  },

  /** raw 프레임 문자열에서 COMMAND 추출 */
  command(raw) {
    if (!raw) return '';
    return raw.split('\n')[0];
  },

  /** raw 프레임 문자열에서 body JSON 파싱 */
  parseBody(raw) {
    const sep = raw.indexOf('\n\n');
    if (sep < 0) return null;
    const body = raw.substring(sep + 2).replace(/\0$/, '');
    try {
      return JSON.parse(body);
    } catch {
      return null;
    }
  },
};
