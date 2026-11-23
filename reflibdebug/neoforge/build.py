import argparse, json, os, shutil, subprocess, sys

ap = argparse.ArgumentParser()
ap.add_argument('--polymc', default=os.path.expandvars(r'%APPDATA%\PolyMC'))
ap.add_argument('--neoforge', default='26.1.2.126')
ap.add_argument('--mc', default='26.1.2')
a = ap.parse_args()
here = os.path.dirname(os.path.abspath(__file__))
libs = os.path.join(a.polymc, 'libraries')
cp = []
for meta in ('net.neoforged.neoforge/%s.json' % a.neoforge, 'net.minecraft/%s.json' % a.mc):
    d = json.load(open(os.path.join(a.polymc, 'meta', meta), encoding='utf8'))
    for l in d.get('libraries', []) + d.get('mavenFiles', []):
        g, n, v, *c = l['name'].split(':')
        p = os.path.join(libs, g.replace('.', os.sep), n, v, '%s-%s%s.jar' % (n, v, '-' + c[0] if c else ''))
        if os.path.exists(p):
            cp.append(p)
cp.append(os.path.join(libs, 'net', 'neoforged', 'minecraft-client-patched', a.neoforge, 'minecraft-client-patched-%s.jar' % a.neoforge))
out = os.path.join(here, 'build')
classes = os.path.join(out, 'classes')
shutil.rmtree(out, ignore_errors=True)
os.makedirs(classes)
srcs = []
for d, _, fs in os.walk(os.path.join(here, 'src')):
    srcs += [os.path.join(d, f) for f in fs if f.endswith('.java')]
subprocess.check_call(['javac', '--release', '25', '-encoding', 'UTF-8', '-d', classes, '-cp', os.pathsep.join(cp)] + srcs)
shutil.copytree(os.path.join(here, 'resources'), classes, dirs_exist_ok=True)
jar = os.path.join(out, 'reflibdebug-neoforge-0.2.0.jar')
subprocess.check_call(['jar', '--create', '--file', jar, '-C', classes, '.'])
print('built', jar)
