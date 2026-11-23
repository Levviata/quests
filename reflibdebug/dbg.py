"""Client for the reflibdebug control port (1.12.2 and NeoForge 26.1.2 helpers share the protocol).

  python -I dbg.py <game folder or alias> "<command>" ["<command>" ...]

Aliases: neo = the PolyMC instance 26.1.2, lv = the PolyMC instance LvNTM, ours = reflibquests/run/client.
Each command is sent on its own connection; the reply is printed. Commands: ping, status, gui, shot <png>, move x y,
click|down|up x y [button], wheel n, key NAME, keyctrl NAME, keyshift NAME, hold NAME, release NAME, type text,
chat text-or-/command, buttons, pressbutton text, worlds, openworld folder, resize w h. "wait 300" sleeps (milliseconds).
Coordinates are window pixels, top-left origin. Real FTB Quests needs: move x y, wait ~250, then click.
"""
import os, socket, sys, time

ALIASES = {
    'neo': os.path.expandvars(r'%APPDATA%\PolyMC\instances\26.1.2\.minecraft'),
    'lv': os.path.expandvars(r'%APPDATA%\PolyMC\instances\LvNTM\minecraft'),
}


def send(game, cmd):
    folder = ALIASES.get(game, game)
    lines = open(os.path.join(folder, 'reflibdebug.port')).read().split()
    port, token = int(lines[0]), lines[1]
    with socket.create_connection(('127.0.0.1', port), timeout=30) as s:
        s.sendall(('%s %s\n' % (token, cmd)).encode('utf8'))
        data = b''
        while not data.endswith(b'\n'):
            chunk = s.recv(65536)
            if not chunk:
                break
            data += chunk
    return data.decode('utf8', 'replace').strip()


if __name__ == '__main__':
    game = sys.argv[1]
    for c in sys.argv[2:]:
        if c.startswith('wait '):
            time.sleep(int(c[5:]) / 1000.0)
            continue
        print(c, '->', send(game, c))
