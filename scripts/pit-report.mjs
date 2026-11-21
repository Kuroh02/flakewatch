#!/usr/bin/env node
// Prints PIT's result in a readable form: the score, then every mutant the tests did not catch
// (where it is and what was changed). Used by the CI `mutation` job; also handy locally:
//   ./mvnw -pl core test-compile org.pitest:pitest-maven:mutationCoverage
//   node scripts/pit-report.mjs core/target/pit-reports/mutations.xml
import { readFileSync, appendFileSync } from 'node:fs';

const file = process.argv[2] ?? 'core/target/pit-reports/mutations.xml';
let xml;
try {
  xml = readFileSync(file, 'utf8');
} catch {
  console.log(`No PIT report at ${file}.`);
  process.exit(0);
}

const field = (block, name) => {
  const m = block.match(new RegExp(`<${name}>([\\s\\S]*?)</${name}>`));
  return m ? m[1].replace(/&lt;/g, '<').replace(/&gt;/g, '>').replace(/&quot;/g, '"').replace(/&amp;/g, '&') : '';
};

const mutants = [...xml.matchAll(/<mutation\b([^>]*)>([\s\S]*?)<\/mutation>/g)].map(([, attrs, body]) => ({
  status: (attrs.match(/status='([A-Z_]+)'/) ?? [])[1] ?? 'UNKNOWN',
  cls: field(body, 'mutatedClass').replace(/^.*\.flakewatch\.core\./, ''),
  method: field(body, 'mutatedMethod'),
  line: field(body, 'lineNumber'),
  what: field(body, 'description'),
}));

const killed = mutants.filter((m) => ['KILLED', 'TIMED_OUT', 'MEMORY_ERROR'].includes(m.status)).length;
const missed = mutants.filter((m) => ['SURVIVED', 'NO_COVERAGE'].includes(m.status));
const score = mutants.length ? Math.round((100 * killed) / mutants.length) : 0;

const lines = [
  `### Mutation score: ${score}% (${killed} of ${mutants.length} mutants caught)`,
  '',
  missed.length ? `Not caught (${missed.length}):` : 'Every mutant was caught.',
  '',
  ...missed
    .sort((a, b) => a.cls.localeCompare(b.cls) || Number(a.line) - Number(b.line))
    .map((m) => `- \`${m.cls}.${m.method}\` line ${m.line}: ${m.what} (${m.status === 'NO_COVERAGE' ? 'no test runs this line' : 'tests still pass'})`),
];
console.log(lines.join('\n'));
if (process.env.GITHUB_STEP_SUMMARY) appendFileSync(process.env.GITHUB_STEP_SUMMARY, lines.join('\n') + '\n');
