import { appendFileSync, readFileSync, writeFileSync } from 'node:fs'
import { pathToFileURL } from 'node:url'

export const MARKER = '<!-- llt-285-validacao-prs -->'
export const RELEASE_NOTE = 'PR de release (Development → main): só testes e build são verificados'
export const BRANCH_FORMAT = 'LLT-XXX-slug'
export const TITLE_FORMAT = 'LLT-XXX: descricao'
export const COMMIT_FORMAT = 'LLT-XXX: descricao ou LLT-XXX descricao'
export const DEFAULT_COMMANDS = {
  ci: './mvnw -B -DskipTests compile',
  test: './mvnw -B test',
  build: './mvnw -B -DskipTests verify',
}
const SKIPPED_DETAIL = 'Não rodaram porque a compilação falhou.'
const RERUN_COMMANDS = new Set(['test', '/test'])
const RERUN_ASSOCIATIONS = new Set(['OWNER', 'MEMBER', 'COLLABORATOR'])

export function isRerunCommand(body) {
  return RERUN_COMMANDS.has(String(body ?? '').trim())
}

export function canRequestRerun(authorAssociation) {
  return RERUN_ASSOCIATIONS.has(String(authorAssociation ?? ''))
}

export function isIgnoredCommentAuthor(user) {
  if (!user) return false
  const type = String(user.type ?? '')
  const login = String(user.login ?? '')
  if (type === 'Bot') return true
  if (login === 'github-actions[bot]' || login.endsWith('[bot]')) return true
  return false
}

export function shouldRerun(input = {}) {
  if (isIgnoredCommentAuthor(input.user)) return false
  if (!canRequestRerun(input.association)) return false
  return isRerunCommand(input.body)
}

export function gateFromEnv(env = process.env) {
  return shouldRerun({
    body: env.COMMENT_BODY,
    association: env.AUTHOR_ASSOCIATION,
    user: { type: env.AUTHOR_TYPE, login: env.AUTHOR_LOGIN },
  })
}

export function rerunReaction(comment) {
  const body = String(comment ?? '')
  if (body.trim() === '' || body.includes('❌')) return '-1'
  return 'rocket'
}

const BRANCH_PATTERN = /^LLT-(\d+)-([a-z0-9]+(?:-[a-z0-9]+)*)$/
const TITLE_PATTERN = /^LLT-(\d+): (.*)$/
const COMMIT_PATTERN = /^LLT-(\d+)(?:: | )(.*)$/

function firstLine(value) {
  return String(value ?? '').split(/\r?\n/, 1)[0]
}

function described(line, pattern) {
  const match = pattern.exec(line)
  if (!match || match[2].startsWith(' ') || match[2].trim() === '') return null
  return { number: match[1], line }
}

function textMatch(value) {
  return described(firstLine(value), TITLE_PATTERN)
}

function commitMatch(value) {
  return described(firstLine(value), COMMIT_PATTERN)
}

export function checkBranch(name) {
  const found = String(name ?? '')
  const match = BRANCH_PATTERN.exec(found)
  if (!match) {
    return {
      name: 'branch',
      ok: false,
      number: null,
      found,
      expected: BRANCH_FORMAT,
      excerpt: '',
      detail: `encontrado ${found}; esperado ${BRANCH_FORMAT}`,
    }
  }
  return { name: 'branch', ok: true, number: match[1], found, expected: BRANCH_FORMAT, excerpt: '', detail: '' }
}

export function checkTitle(title, branchNumber) {
  const found = firstLine(title)
  const match = textMatch(found)
  if (!match) {
    return {
      name: 'título',
      ok: false,
      number: null,
      found,
      expected: TITLE_FORMAT,
      excerpt: '',
      detail: `encontrado ${found}; esperado ${TITLE_FORMAT}`,
    }
  }
  if (branchNumber !== null && match.number !== branchNumber) {
    return {
      name: 'título',
      ok: false,
      number: match.number,
      found,
      expected: TITLE_FORMAT,
      excerpt: '',
      detail: `encontrado ${found}; esperado ${TITLE_FORMAT}; números ${branchNumber} e ${match.number}`,
    }
  }
  return { name: 'título', ok: true, number: match.number, found, expected: TITLE_FORMAT, excerpt: '', detail: '' }
}

export function checkCommits(commits) {
  const failures = []
  for (const commit of commits ?? []) {
    const parents = Array.isArray(commit.parents) ? commit.parents.length : Number(commit.parents ?? 0)
    if (parents > 1) continue
    const line = firstLine(commit.message)
    if (!commitMatch(line)) failures.push({ hash: String(commit.sha ?? ''), line })
  }
  if (failures.length === 0) {
    return { name: 'commits', ok: true, found: '', expected: COMMIT_FORMAT, excerpt: '', detail: '' }
  }
  const detail = failures.map((item) => `${item.hash} ${item.line}`).join('; ')
  return {
    name: 'commits',
    ok: false,
    found: detail,
    expected: COMMIT_FORMAT,
    excerpt: '',
    detail,
  }
}

export function clipExcerpt(output) {
  const raw = String(output ?? '')
  const lines = raw.split(/\r?\n/)
  const overLines = lines.length > 30
  let text = (overLines ? lines.slice(-30) : lines).join('\n')
  const overChars = text.length > 1500
  if (overChars) text = text.slice(-1500)
  if (overLines || overChars) return `${text}\nO trecho foi cortado.`
  return text
}

function commandCheck(name, command, result, skipped) {
  if (skipped) {
    return {
      name,
      ok: false,
      found: 'não executado',
      expected: command,
      excerpt: '',
      detail: SKIPPED_DETAIL,
    }
  }
  if (result?.ok) return { name, ok: true, found: '', expected: command, excerpt: '', detail: '' }
  const excerpt = clipExcerpt(result?.output ?? '')
  return {
    name,
    ok: false,
    found: command,
    expected: `${command} conclui`,
    excerpt,
    detail: [`comando ${command}`, excerpt].filter(Boolean).join('\n'),
  }
}

export function commandNamesFrom(commandNames = {}) {
  return {
    ci: commandNames?.ci || DEFAULT_COMMANDS.ci,
    test: commandNames?.test || DEFAULT_COMMANDS.test,
    build: commandNames?.build || DEFAULT_COMMANDS.build,
  }
}

export function commandChecks(commands = {}, commandNames = DEFAULT_COMMANDS) {
  const names = commandNamesFrom(commandNames)
  const compileOk = Boolean(commands.ci?.ok)
  return [
    commandCheck(names.ci, names.ci, commands.ci, false),
    commandCheck(names.test, names.test, commands.test, !compileOk),
    commandCheck(names.build, names.build, commands.build, !compileOk),
  ]
}

export function evaluate(input) {
  const branch = checkBranch(input.branch)
  const title = checkTitle(input.title, branch.number)
  const commits = checkCommits(input.commits)
  return [branch, title, commits, ...commandChecks(input.commands, input.commandNames)]
}

function fixText(check) {
  const parts = [`- ${check.name}`]
  if (check.found) parts.push(`encontrado: ${check.found}`)
  if (check.expected) parts.push(`esperado: ${check.expected}`)
  if (check.excerpt) parts.push(`trecho:\n${check.excerpt}`)
  return parts.join('\n')
}

export function buildComment(input) {
  const release = input.base === 'main'
  const checks = release ? commandChecks(input.commands, input.commandNames) : evaluate(input)
  const lines = [MARKER, '']
  if (release) lines.push(RELEASE_NOTE, '')
  for (const check of checks) {
    lines.push(`${check.ok ? '✅' : '❌'} ${check.name}${check.detail ? ` — ${check.detail}` : ''}`)
  }
  lines.push('', 'Para corrigir com IA', '')
  const failed = checks.filter((check) => !check.ok)
  if (failed.length === 0) lines.push('Não há nada para corrigir.')
  else lines.push('```text', ...failed.map(fixText), '```')
  return `${lines.join('\n')}\n`
}

function readOptional(file) {
  if (!file) return ''
  try {
    return readFileSync(file, 'utf8')
  } catch {
    return ''
  }
}

export function inputFromEnv(env = process.env) {
  return {
    base: env.PR_BASE ?? '',
    branch: env.PR_BRANCH ?? '',
    title: env.PR_TITLE ?? '',
    commits: JSON.parse(readFileSync(env.PR_COMMITS_FILE, 'utf8')),
    commandNames: commandNamesFrom({
      ci: env.CI_COMMAND,
      test: env.TEST_COMMAND,
      build: env.BUILD_COMMAND,
    }),
    commands: {
      ci: { ok: env.CI_CODE === '0', output: readOptional(env.CI_OUT) },
      test: env.TEST_RAN === '1' ? { ok: env.TEST_CODE === '0', output: readOptional(env.TEST_OUT) } : null,
      build: env.BUILD_RAN === '1' ? { ok: env.BUILD_CODE === '0', output: readOptional(env.BUILD_OUT) } : null,
    },
  }
}

function writeOutput(name, value) {
  const line = `${name}=${value}\n`
  if (process.env.GITHUB_OUTPUT) appendFileSync(process.env.GITHUB_OUTPUT, line)
  process.stdout.write(line)
}

function main() {
  try {
    if (process.argv[2] === '--gate') {
      writeOutput('rerun', gateFromEnv() ? 'true' : 'false')
    } else if (process.argv[2] === '--reaction') {
      let body = ''
      try {
        body = readFileSync(process.env.COMMENT_FILE ?? '', 'utf8')
      } catch {
        body = ''
      }
      writeOutput('reaction', rerunReaction(body))
    } else {
      const output = process.argv[3]
      const body = buildComment(process.argv[2] === '--env' ? inputFromEnv() : JSON.parse(readFileSync(process.argv[2], 'utf8')))
      if (output) writeFileSync(output, body)
      else process.stdout.write(body)
    }
  } catch (error) {
    process.stderr.write(`${error instanceof Error ? error.message : 'Falha ao montar o comentário.'}\n`)
  }
  process.exit(0)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) main()
