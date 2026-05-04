import { createRequire } from 'node:module';
import process from 'node:process';

const require = createRequire(import.meta.url);
const requireFromChangesetsCli = createRequire(require.resolve('@changesets/cli/package.json'));
const readChangesets = requireFromChangesetsCli('@changesets/read').default;

const changesets = await readChangesets(process.cwd());
const suffix = changesets.length === 1 ? '' : 's';

process.stdout.write(`Validated ${changesets.length} changeset${suffix}.\n`);
