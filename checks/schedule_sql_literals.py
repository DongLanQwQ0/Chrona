"""Read the production SQL string concatenations without requiring Java in quick checks."""
import json
import math
from pathlib import Path
import re

root = Path(__file__).resolve().parents[1]
source = root / 'app/src/main/java/com/donglan/chrona/data'
declarations = dict(re.findall(r'public static final String (\w+)\s*=\s*(.*?);',
                              (source / 'ScheduleQuery.java').read_text(encoding='utf-8'), re.S))
duration = re.search(r'DEFAULT_EXECUTION_DURATION_MILLIS\s*=\s*([\d_L *]+);',
                     (source / 'EventCandidate.java').read_text(encoding='utf-8'))[1]
constants = {'EventCandidate.DEFAULT_EXECUTION_DURATION_MILLIS': str(math.prod(
    int(part.strip().replace('_', '').removesuffix('L')) for part in duration.split('*')))}
token = re.compile(r'"(?:[^"\\]|\\.)*"|(?:ScheduleQuery\.|EventCandidate\.)?\w+')


def sql_expression(expression):
    parts = []
    for match in token.finditer(expression):
        value = match[0]
        if value.startswith('"'):
            parts.append(json.loads(value))
        else:
            name = value.removeprefix('ScheduleQuery.')
            if name not in constants:
                constants[name] = sql_expression(declarations[name])
            parts.append(constants[name])
    if re.sub(r'[\s+,]', '', token.sub('', expression)):
        raise AssertionError('Unsupported production SQL expression')
    return ''.join(parts)
