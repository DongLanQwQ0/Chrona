"""Existing offline JVM recurrence dependencies; never download or install anything."""
import os
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DATA = ROOT / 'app/src/main/java/com/donglan/chrona/data'


def recurrence_classpath():
    cache = Path(os.environ.get('GRADLE_USER_HOME', 'F:/Android/GradleCache')) / 'caches/modules-2/files-2.1'
    jars = [ROOT / 'build/ai-checks/json.jar']
    for module, version in [('net.sf.biweekly/biweekly', '0.6.8'),
                            ('com.github.mangstadt/vinnie', '2.0.2')]:
        found = [p for p in (cache / module / version).glob('*/*.jar')
                 if not p.name.endswith(('-sources.jar', '-javadoc.jar'))]
        if len(found) != 1:
            raise RuntimeError(f'Expected one existing cached {module}:{version} JAR, got {found}')
        jars.extend(found)
    if not jars[0].is_file():
        raise RuntimeError('Existing build/ai-checks/json.jar is required')
    return os.pathsep.join(map(str, jars))


def recurrence_java(command):
    """Add real model dependencies and project-local JVM temporary storage."""
    command = list(command)
    executable = Path(command[0]).stem
    if executable not in ('javac', 'java'):
        return command
    temporary_option = '-J-Djava.io.tmpdir=' if executable == 'javac' else '-Djava.io.tmpdir='
    if not any(str(argument).startswith(temporary_option) for argument in command[1:]):
        temporary = ROOT / 'build/tmp'
        temporary.mkdir(parents=True, exist_ok=True)
        command.insert(1, temporary_option + str(temporary))
    dependency_cp = recurrence_classpath()
    if '-cp' in command:
        index = command.index('-cp') + 1
        command[index] = dependency_cp + os.pathsep + str(command[index])
    else:
        command[1:1] = ['-cp', dependency_cp]
    if executable == 'javac':
        for name in ('EventCandidate.java', 'EventCategory.java', 'RepeatRule.java', 'RepeatOccurrences.java'):
            source = str(DATA / name)
            if source not in command:
                command.append(source)
    return command
