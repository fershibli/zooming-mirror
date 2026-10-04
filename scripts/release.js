#!/usr/bin/env node
/**
 * Automatic versioning based on conventional commits, with no external
 * dependencies. Reads the commits since the last tag, decides the semver
 * bump, updates package.json/app.json, prepends CHANGELOG.md and creates the
 * tag. Used by the release workflow (.github/workflows/release.yml).
 *
 * Usage: node scripts/release.js [--dry-run]
 */

const { execSync } = require('node:child_process');
const fs = require('node:fs');
const path = require('node:path');

const dryRun = process.argv.includes('--dry-run');
const root = path.join(__dirname, '..');

function sh(command) {
  return execSync(command, { cwd: root, encoding: 'utf8' }).trim();
}

function lastTag() {
  try {
    return sh('git describe --tags --abbrev=0');
  } catch {
    return null;
  }
}

/** @returns {{hash: string, subject: string, body: string}[]} */
function commitsSince(tag) {
  const range = tag ? `${tag}..HEAD` : 'HEAD';
  const raw = sh(`git log ${range} --format=%H%x1f%s%x1f%b%x1e`);
  if (!raw) {
    return [];
  }
  return raw
    .split('\x1e')
    .map((entry) => entry.trim())
    .filter(Boolean)
    .map((entry) => {
      const [hash, subject, body = ''] = entry.split('\x1f');
      return { hash, subject, body };
    });
}

function tagExists(tag) {
  try {
    sh(`git rev-parse -q --verify refs/tags/${tag}`);
    return true;
  } catch {
    return false;
  }
}

const RELEVANT = /^(feat|fix|perf|refactor|revert)(\(.+\))?!?:/;

function bumpFor(commits) {
  let bump = null;
  for (const commit of commits) {
    if (/^\w+(\(.+\))?!:/.test(commit.subject) || /BREAKING CHANGE/.test(commit.body)) {
      return 'major';
    }
    if (/^feat(\(.+\))?:/.test(commit.subject)) {
      bump = 'minor';
    } else if (bump === null && RELEVANT.test(commit.subject)) {
      bump = 'patch';
    }
  }
  return bump;
}

function nextVersion(current, bump) {
  const [major, minor, patch] = current.split('.').map(Number);
  if (bump === 'major') {
    return `${major + 1}.0.0`;
  }
  if (bump === 'minor') {
    return `${major}.${minor + 1}.0`;
  }
  return `${major}.${minor}.${patch + 1}`;
}

/** Android versionCode derived only from semver: 1.0.4 → 1000004. */
function versionCodeFor(version) {
  const [major, minor, patch] = version.split('.').map(Number);
  return major * 1_000_000 + minor * 1_000 + patch;
}

function updateJson(file, version) {
  const filePath = path.join(root, file);
  const data = JSON.parse(fs.readFileSync(filePath, 'utf8'));
  if (file === 'app.json') {
    data.expo.version = version;
    data.expo.android = data.expo.android ?? {};
    data.expo.android.versionCode = versionCodeFor(version);
  } else {
    data.version = version;
  }
  fs.writeFileSync(filePath, `${JSON.stringify(data, null, 2)}\n`);
}

const SECTIONS = [
  ['feat', 'Novidades'],
  ['fix', 'Correções'],
  ['perf', 'Desempenho'],
  ['refactor', 'Refatorações'],
  ['revert', 'Reversões'],
];

function changelogSection(version, commits) {
  const date = new Date().toISOString().slice(0, 10);
  let text = `## v${version} (${date})\n`;
  for (const [type, heading] of SECTIONS) {
    const matching = commits.filter((commit) =>
      new RegExp(`^${type}(\\(.+\\))?!?:`).test(commit.subject),
    );
    if (matching.length === 0) {
      continue;
    }
    text += `\n### ${heading}\n\n`;
    for (const commit of matching) {
      const subject = commit.subject.replace(/^\w+(\(.+\))?!?:\s*/, '');
      text += `- ${subject} (${commit.hash.slice(0, 7)})\n`;
    }
  }
  return `${text}\n`;
}

function prependChangelog(section) {
  const filePath = path.join(root, 'CHANGELOG.md');
  const header = '# Changelog\n\n';
  const existing = fs.existsSync(filePath)
    ? fs.readFileSync(filePath, 'utf8').replace(header, '')
    : '';
  fs.writeFileSync(filePath, header + section + existing);
}

function setGithubOutput(name, value) {
  if (!process.env.GITHUB_OUTPUT) {
    return;
  }
  fs.appendFileSync(process.env.GITHUB_OUTPUT, `${name}=${value}\n`);
}

function main() {
  const tag = lastTag();
  const commits = commitsSince(tag);
  const bump = bumpFor(commits);

  if (!bump) {
    console.log('Nenhum commit relevante desde a última tag; nada a versionar.');
    setGithubOutput('tag', '');
    return;
  }

  const current = JSON.parse(fs.readFileSync(path.join(root, 'package.json'), 'utf8')).version;
  let version = tag ? nextVersion(current, bump) : current;

  // An interrupted run can leave a tag behind with nothing on the branch to
  // show for it. Repeating the same bump keeps the release the size it was
  // meant to be — a minor stays a minor — instead of demoting it to a patch.
  while (tagExists(`v${version}`)) {
    console.warn(`A tag v${version} já existe; repetindo o bump ${bump}.`);
    const next = nextVersion(version, bump);
    if (next === version) {
      console.error(`Não foi possível passar da tag v${version}.`);
      process.exit(1);
    }
    version = next;
  }

  console.log(`Última tag: ${tag ?? '(nenhuma)'} · bump: ${bump} · nova versão: ${version}`);

  if (dryRun) {
    console.log(changelogSection(version, commits));
    setGithubOutput('tag', '');
    return;
  }

  updateJson('package.json', version);
  updateJson('app.json', version);
  prependChangelog(changelogSection(version, commits));

  sh('git add package.json app.json CHANGELOG.md');
  sh(`git commit -m "chore(release): v${version} [skip ci]"`);
  // Annotated tag: git push --follow-tags only pushes annotated tags.
  sh(`git tag -a v${version} -m "v${version}"`);
  setGithubOutput('tag', `v${version}`);
  console.log(`Release v${version} criada. Faça push com --follow-tags.`);
}

main();
