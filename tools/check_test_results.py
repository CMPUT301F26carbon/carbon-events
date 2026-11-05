"""Fail CI if a requested suite silently discovers no tests or contains failures."""
from pathlib import Path
import argparse
import sys
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser()
parser.add_argument('suite', choices=['unit', 'android'])
args = parser.parse_args()
base = Path('app/build/test-results/testDebugUnitTest') if args.suite == 'unit' else Path('app/build/outputs/androidTest-results/connected')
files = list(base.rglob('TEST-*.xml'))
cases = [case for p in files for case in ET.parse(p).getroot().iter('testcase')]
failed = [c for c in cases if c.find('failure') is not None or c.find('error') is not None]
executed = [c for c in cases if c.find('skipped') is None]
minimum = 6 if args.suite == 'android' else 1
print(f'{args.suite}: {len(executed)} executed, {len(cases)-len(executed)} skipped, {len(failed)} failed')
if len(executed) < minimum or failed:
    sys.exit('Expected tests did not execute successfully; inspect the uploaded reports.')
