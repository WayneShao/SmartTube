"""Bounded, read-only Android playback samples. Output may contain private media metadata."""
import argparse
import datetime
import json
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--adb', required=True)
    parser.add_argument('--serial', required=True)
    parser.add_argument('--output', required=True)
    parser.add_argument('--label', required=True)
    parser.add_argument('--package', default='app.smarttube.rayneo')
    parser.add_argument('--duration', type=int, default=120)
    parser.add_argument('--interval', type=int, default=10)
    args = parser.parse_args()
    if not 1 <= args.duration <= 600 or not 1 <= args.interval <= 30:
        parser.error('duration must be 1..600 seconds; interval 1..30 seconds')
    import re
    if not re.fullmatch(r'[A-Za-z][A-Za-z0-9_.]+', args.package):
        parser.error('Invalid package name')
    output = Path(args.output)
    output.mkdir(parents=True, exist_ok=False)

    def shell(command):
        try:
            result = subprocess.run([args.adb, '-s', args.serial, 'shell', command],
                                    capture_output=True, text=True, encoding='utf-8', errors='replace', timeout=12)
            return {'code': result.returncode, 'stdout': result.stdout, 'stderr': result.stderr}
        except subprocess.TimeoutExpired:
            return {'code': None, 'stdout': '', 'stderr': 'query timeout'}

    def required(command):
        result = shell(command)
        if result['code'] != 0:
            raise RuntimeError(result)
        return result['stdout'].strip()

    boot = required('cat /proc/sys/kernel/random/boot_id')
    pid = required('pidof ' + args.package)
    if not pid.isdecimal():
        raise RuntimeError('Expected exactly one running app process')
    ticks = int(required('getconf CLK_TCK'))
    header = {'label': args.label, 'utc': datetime.datetime.now(datetime.timezone.utc).isoformat(),
              'package': args.package, 'pid': pid, 'boot_id': boot, 'clock_ticks': ticks, 'duration': args.duration,
              'interval': args.interval, 'cpu_unit': 'percent of one CPU core',
              'gpu_scope': 'whole device; raw busy/total, not app-specific',
              'battery_temperature_scope': 'battery sensor, not temple surface'}
    (output / 'metadata.json').write_text(json.dumps(header, indent=2), encoding='utf-8')
    for name, command in {
        'package': 'dumpsys package ' + args.package,
        'window': 'dumpsys window windows',
        'media-start': 'dumpsys media.metrics',
        'memory-start': 'dumpsys meminfo ' + pid,
        'thermal-start': 'dumpsys thermalservice',
    }.items():
        (output / (name + '.json')).write_text(json.dumps(shell(command), ensure_ascii=False), encoding='utf-8')
    previous = None
    samples = []
    started = time.monotonic()
    with (output / 'samples.jsonl').open('w', encoding='utf-8') as stream:
        while True:
            record = {'host_elapsed': time.monotonic() - started}
            identity = shell('cat /proc/sys/kernel/random/boot_id; pidof ' + args.package)
            record['identity'] = identity
            if identity['code'] != 0 or identity['stdout'].split() != [boot, pid]:
                record['stopped'] = 'transport, boot or process changed; no restart attempted'
                stream.write(json.dumps(record) + '\n'); stream.flush()
                raise RuntimeError(record['stopped'])
            record['process'] = shell('cat /proc/uptime; cat /proc/' + pid + '/stat')
            record['battery'] = shell('dumpsys battery')
            record['gpu'] = shell('cat /sys/class/kgsl/kgsl-3d0/gpubusy')
            raw = record['process']['stdout'].splitlines()
            if record['process']['code'] == 0 and len(raw) == 2:
                uptime = float(raw[0].split()[0])
                fields = raw[1].rsplit(')', 1)[1].split()
                cpu_ticks = int(fields[11]) + int(fields[12])
                if previous and uptime > previous[0]:
                    record['cpu_percent_one_core'] = 100 * (cpu_ticks - previous[1]) / ticks / (uptime - previous[0])
                    samples.append((cpu_ticks - previous[1], uptime - previous[0]))
                previous = (uptime, cpu_ticks)
            stream.write(json.dumps(record) + '\n'); stream.flush()
            print(json.dumps({k: record[k] for k in ('host_elapsed', 'cpu_percent_one_core') if k in record}), flush=True)
            remaining = args.duration - (time.monotonic() - started)
            if remaining <= 0:
                break
            time.sleep(min(args.interval, remaining))
    for name, command in {
        'memory-end': 'dumpsys meminfo ' + pid,
        'thermal-end': 'dumpsys thermalservice',
        'media-end': 'dumpsys media.metrics',
        'gfx-end': 'dumpsys gfxinfo ' + args.package,
    }.items():
        (output / (name + '.json')).write_text(json.dumps(shell(command), ensure_ascii=False), encoding='utf-8')
    summary = {'valid_cpu_intervals': len(samples), 'cpu_percent_one_core':
               100 * sum(t for t, _ in samples) / ticks / sum(s for _, s in samples) if samples else None}
    (output / 'summary.json').write_text(json.dumps(summary, indent=2), encoding='utf-8')
    print(json.dumps(summary), flush=True)


if __name__ == '__main__':
    main()
