// Starts real servers on localhost, connects mineflayer bots and checks what the plugin allows.
// usage: node run.js <work dir> <plugin jar> <target,target,...>
const { spawn } = require('child_process')
const fs = require('fs')
const path = require('path')
const https = require('https')
const mineflayer = require('mineflayer')

const [workDir, pluginJar, targetList] = process.argv.slice(2)
const JAVA = { 8: process.env.JAVA8, 17: process.env.JAVA17 || process.env.JAVA21, 21: process.env.JAVA21, 25: process.env.JAVA25 }

// clientVersion is what the bot speaks; it has to be a version mineflayer knows
const TARGETS = {
  'paper-1.13.2': { project: 'paper', version: '1.13.2', java: 8, client: '1.13.2', port: 25601 },
  'paper-1.16.5': { project: 'paper', version: '1.16.5', java: 8, client: '1.16.5', port: 25602 },
  'paper-1.20.4': { project: 'paper', version: '1.20.4', java: 21, client: '1.20.4', port: 25603 },
  'paper-1.21.11': { project: 'paper', version: '1.21.11', java: 21, client: '1.21.11', port: 25604 },
  'paper-26.1.2': { project: 'paper', version: '26.1.2', java: 25, client: '26.1', port: 25610 },
  'velocity-4.3.1': { proxy: 'velocity', version: '4.3.1', backend: 'paper-1.21.11', java: 25, client: '1.21.11', port: 25611 },
  'folia-1.20.4': { project: 'folia', version: '1.20.4', java: 21, client: '1.20.4', port: 25605 },
  'folia-1.21.11': { project: 'folia', version: '1.21.11', java: 21, client: '1.21.11', port: 25606 },
  'spigot-1.21.11': { jar: 'spigot-1.21.11.jar', java: 21, client: '1.21.11', port: 25607 },
  'velocity-3.5.1': { proxy: 'velocity', version: '3.5.1', backend: 'paper-1.21.11', java: 21, client: '1.21.11', port: 25608 },
  'waterfall-1.21': { proxy: 'waterfall', version: '1.21', backend: 'paper-1.20.4', java: 21, client: '1.20.4', port: 25609 }
}

const sleep = ms => new Promise(resolve => setTimeout(resolve, ms))

function getJson (url) {
  return new Promise((resolve, reject) => https.get(url, { headers: { 'User-Agent': 'commandwhitelist-e2e' } }, res => {
    let data = ''
    res.on('data', c => { data += c })
    res.on('end', () => { try { resolve(JSON.parse(data)) } catch (e) { reject(new Error(url + ': ' + data.slice(0, 200))) } })
  }).on('error', reject))
}

function download (url, file) {
  return new Promise((resolve, reject) => https.get(url, { headers: { 'User-Agent': 'commandwhitelist-e2e' } }, res => {
    if (res.statusCode >= 300 && res.statusCode < 400) return download(res.headers.location, file).then(resolve, reject)
    if (res.statusCode !== 200) return reject(new Error(url + ' -> ' + res.statusCode))
    const out = fs.createWriteStream(file)
    res.pipe(out)
    out.on('finish', () => out.close(resolve))
  }).on('error', reject))
}

async function fetchJar (project, version) {
  const file = path.join(workDir, 'jars', `${project}-${version}.jar`)
  if (fs.existsSync(file)) return file
  fs.mkdirSync(path.dirname(file), { recursive: true })
  const builds = await getJson(`https://fill.papermc.io/v3/projects/${project}/versions/${version}/builds`)
  const build = Array.isArray(builds) ? builds[0] : builds.builds[0]
  const url = build.downloads['server:default'].url
  console.log(`download ${path.basename(file)} from ${url}`)
  await download(url, file + '.part')
  fs.renameSync(file + '.part', file)
  return file
}

class Server {
  constructor (name, jar, java, dir, args = []) {
    this.name = name
    this.dir = dir
    this.log = ''
    this.proc = spawn(JAVA[java], ['-Xms512M', '-Xmx1500M', '-Dcom.mojang.eula.agree=true', '-DPaper.IgnoreJavaVersion=true', '-jar', jar, ...args], { cwd: dir })
    const onData = d => { this.log += d.toString() }
    this.proc.stdout.on('data', onData)
    this.proc.stderr.on('data', onData)
    this.exited = new Promise(resolve => this.proc.on('exit', resolve))
  }

  async waitFor (regex, ms, from = 0) {
    const end = Date.now() + ms
    while (Date.now() < end) {
      if (regex.test(this.log.slice(from))) return true
      if (this.proc.exitCode !== null) throw new Error(`${this.name} exited: ${this.log.slice(-1500)}`)
      await sleep(250)
    }
    throw new Error(`${this.name}: timeout on ${regex}. Log end:\n${this.log.slice(-1500)}`)
  }

  command (line) { this.proc.stdin.write(line + '\n') }

  async stop (cmd = 'stop') {
    if (this.proc.exitCode !== null) return
    this.command(cmd)
    await Promise.race([this.exited, sleep(40000)])
    if (this.proc.exitCode === null) this.proc.kill()
    await this.exited
  }
}

function prepareDir (name, config) {
  const dir = path.join(workDir, 'run', name)
  fs.rmSync(dir, { recursive: true, force: true })
  const plugins = path.join(dir, 'plugins')
  fs.mkdirSync(path.join(plugins, 'CommandWhitelist'), { recursive: true })
  fs.mkdirSync(path.join(plugins, 'bStats'), { recursive: true })
  fs.writeFileSync(path.join(plugins, 'bStats', 'config.yml'), 'enabled: false\n')
  fs.copyFileSync(pluginJar, path.join(plugins, 'CommandWhitelist.jar'))
  fs.writeFileSync(path.join(dir, 'eula.txt'), 'eula=true\n')
  if (config) fs.writeFileSync(path.join(plugins, 'CommandWhitelist', 'config.yml'), config)
  fs.writeFileSync(path.join(dir, 'permissions.yml'), 'minecraft.command.time:\n  default: true\n')
  return dir
}

function serverProperties (dir, port) {
  fs.writeFileSync(path.join(dir, 'server.properties'), [
    'online-mode=false', `server-port=${port}`, 'server-ip=127.0.0.1', 'level-type=flat', 'generate-structures=false',
    'spawn-protection=0', 'view-distance=2', 'simulation-distance=2', 'enforce-secure-profile=false', 'enable-command-block=false', 'spawn-animals=false', 'spawn-monsters=false', ''
  ].join('\n'))
}

function connect (name, port, version) {
  return new Promise((resolve, reject) => {
    const bot = mineflayer.createBot({ host: '127.0.0.1', port, username: name, version, auth: 'offline', checkTimeoutInterval: 120000 })
    bot.chatLog = []
    bot.on('messagestr', m => bot.chatLog.push(m))
    bot.tree = null
    bot._client.on('declare_commands', packet => { bot.tree = packet })
    const timer = setTimeout(() => reject(new Error('bot spawn timeout')), 90000)
    bot.once('spawn', () => { clearTimeout(timer); resolve(bot) })
    bot.once('kicked', reason => { clearTimeout(timer); reject(new Error('kicked: ' + JSON.stringify(reason))) })
    bot.once('error', err => { clearTimeout(timer); reject(err) })
  })
}

// Names of the tree nodes reachable from the root by following the given literal path
function treeChildren (bot, ...words) {
  if (!bot.tree) return null
  let node = bot.tree.nodes[bot.tree.rootIndex]
  for (const word of words) {
    const next = node.children.map(i => bot.tree.nodes[i]).find(n => n.extraNodeData && n.extraNodeData.name === word)
    if (!next) return []
    node = next
  }
  return node.children.map(i => bot.tree.nodes[i]).map(n => n.extraNodeData && n.extraNodeData.name).filter(Boolean)
}

class Report {
  constructor (target) { this.target = target; this.rows = [] }
  check (name, ok, detail = '') {
    this.rows.push({ name, ok: !!ok, detail })
    console.log(`  ${ok ? 'PASS' : 'FAIL'} ${name}${detail ? ' :: ' + detail : ''}`)
  }

  info (name, detail) {
    this.rows.push({ name, ok: null, detail, info: true })
    console.log(`  INFO ${name} :: ${detail}`)
  }

  skip (name, why) {
    this.rows.push({ name, ok: null, detail: why })
    console.log(`  SKIP ${name} :: ${why}`)
  }
}

// Sends a command and returns the chat lines it produced
async function send (bot, line, wait = 900) {
  const from = bot.chatLog.length
  bot.chat(line)
  await sleep(wait)
  return bot.chatLog.slice(from).join(' | ')
}

const DENIED = 'DENIED-CMD'
const SUBDENIED = 'DENIED-SUB'
const MESSAGES = `messages:\n  prefix: ''\n  command_denied: ${DENIED} <command>\n  subcommand_denied: ${SUBDENIED}\n`
const was = (out, marker) => out.includes(marker)

function whitelistConfig () {
  return MESSAGES + `mode:\n  commands: WHITELIST\n  subcommands: BLACKLIST\ngroups:\n  default:\n    commands: [help, me, version, gamemode, '/seed']\n    subcommands: ['gamemode creative']\n  vip:\n    commands: [time]\n    subcommands: []\n`
}

function blacklistConfig () {
  return MESSAGES + `mode:\n  commands: BLACKLIST\n  subcommands: BLACKLIST\ngroups:\n  default:\n    commands: [seed, plugins, teleport]\n    subcommands: ['gamemode creative', 'time set']\n`
}

async function backendSuite (r, server, target) {
  const v = target.client

  // ---- WHITELIST
  server.command('whitelist off')
  let bot = await connect('BotUser', target.port, v)
  await sleep(1500)
  const rootNames = treeChildren(bot)
  r.check('whitelist: tree hides unlisted commands', rootNames && !rootNames.includes('stop') && !rootNames.includes('tp'), rootNames ? rootNames.join(',').slice(0, 120) : 'no tree')
  r.check('whitelist: tree has listed command', rootNames && rootNames.includes('version'))

  let out = await send(bot, '/ban Someone')
  r.check('whitelist: unlisted /ban denied', was(out, DENIED), out)
  r.check('whitelist: <command> placeholder filled', out.includes('ban Someone'), out)
  out = await send(bot, '/minecraft:seed')
  r.check('whitelist: namespace not listed denied', was(out, DENIED), out)
  out = await send(bot, '/Version')
  r.check('#01 whitelist: /Version (wrong case) denied', was(out, DENIED), out)
  out = await send(bot, '/version')
  r.check('whitelist: /version allowed', !was(out, DENIED), out)
  out = await send(bot, '/seed')
  r.check('#01 entry written with a slash works', !was(out, DENIED), out)
  out = await send(bot, '/gamemode creative')
  r.check('blacklisted subcommand denied', was(out, SUBDENIED), out)
  out = await send(bot, '/gamemode CREATIVE')
  r.check('blacklisted subcommand in other case denied', was(out, SUBDENIED), out)

  // ---- union of groups, permission seen at once
  out = await send(bot, '/time query day')
  r.check('#12 without group /time denied', was(out, DENIED), out)
  server.command('op BotUser')
  await sleep(1500)
  out = await send(bot, '/time query day')
  r.check('bypass: op is not filtered', !was(out, DENIED), out)
  out = await send(bot, '/VERSION')
  r.info('#01 what the platform does with /VERSION when nothing filters it', out.slice(0, 90))
  server.command('deop BotUser')
  await sleep(1500)

  // ---- /cw as op: add, remove, custom message kept, invalid entry
  const configFile = path.join(server.dir, 'plugins', 'CommandWhitelist', 'config.yml')
  fs.writeFileSync(configFile, fs.readFileSync(configFile, 'utf8').replace('vip:\n', 'vip:\n    custom_command_denied_message: keep-me\n'))
  server.command('cw reload')
  await sleep(2500)
  out = await send(bot, '/time query day')
  r.check('#24 custom message of the missing group', was(out, 'keep-me'), out)
  let from = server.log.length
  server.command('cw add vip tellraw')
  await server.waitFor(/Whitelisted command|tellraw/, 10000, from)
  await sleep(800)
  r.check('#04 /cw add keeps the custom message', fs.readFileSync(configFile, 'utf8').includes('keep-me'))
  r.check('#N13 /cw add saved', fs.readFileSync(configFile, 'utf8').includes('tellraw'))
  r.check('#09 no %s in the answer', !server.log.slice(from).includes('%s'), server.log.slice(from).trim().slice(0, 120))
  from = server.log.length
  server.command('cw addsub default help secret')
  await sleep(1500)
  r.check('#N13 /cw addsub saved', fs.readFileSync(configFile, 'utf8').includes('help secret'))
  server.command('cw remove vip tellraw')
  await sleep(1500)
  r.check('/cw remove saved', !fs.readFileSync(configFile, 'utf8').includes('tellraw'))

  // ---- reload: removed group loses effect, broken file reported
  const good = fs.readFileSync(configFile, 'utf8')
  fs.writeFileSync(configFile, good.replace(/\n\s*- version(?=\n)/, ''))
  r.check('test setup: version removed from file', !/- version\n/.test(fs.readFileSync(configFile, 'utf8')))
  from = server.log.length
  server.command('cw reload')
  await server.waitFor(/Configuration reloaded/, 15000, from)
  r.check('#07 reload does not log thread errors', !/IllegalState|may only be triggered|ConcurrentModification/.test(server.log.slice(from)))
  await sleep(1500)
  const after = treeChildren(bot)
  r.check('#07 tree is refreshed after reload', after && !after.includes('version'), after ? after.join(',').slice(0, 100) : 'none')
  out = await send(bot, '/version')
  r.check('reload applied to execution', was(out, DENIED), out)

  const brokenText = 'groups:\n  default: [\n  : :\n'
  fs.writeFileSync(configFile, brokenText)
  from = server.log.length
  server.command('cw reload')
  await server.waitFor(/could not be loaded/, 15000, from)
  r.check('#N03 broken file: admin is told', true)
  r.check('#N03 broken file is not overwritten', fs.readFileSync(configFile, 'utf8') === brokenText)
  out = await send(bot, '/ban x')
  r.check('#N03 previous rules still active', was(out, DENIED), out)
  fs.writeFileSync(configFile, good)
  bot.quit()
  await sleep(800)

  // ---- BLACKLIST
  fs.writeFileSync(configFile, blacklistConfig())
  from = server.log.length
  server.command('cw reload')
  await server.waitFor(/Configuration reloaded/, 15000, from)
  bot = await connect('BotUser2', target.port, v)
  await sleep(1500)
  const blRoot = treeChildren(bot)
  r.check('blacklist: tree hides listed command', blRoot && !blRoot.includes('seed'))
  out = await send(bot, '/seed')
  r.check('blacklist: /seed denied', was(out, DENIED), out)
  out = await send(bot, '/SEED')
  r.check('#01 blacklist: /SEED denied, no case bypass', was(out, DENIED), out)
  out = await send(bot, '/minecraft:seed')
  r.check('blacklist: namespace denied', was(out, DENIED), out)
  out = await send(bot, '/pl')
  r.check('#N04 blacklist of plugins blocks its alias /pl', was(out, DENIED), out.slice(0, 100))
  out = await send(bot, '/tp 0 0 0')
  if (target.isPaper && !v.startsWith('1.13') && !v.startsWith('1.16')) r.check('#N04 blacklist of teleport blocks the redirect /tp', was(out, DENIED), out.slice(0, 100))
  else r.info('#N04 redirect /tp of a blacklisted teleport on this server', out.slice(0, 100))
  out = await send(bot, '/gamemode creative')
  r.check('blacklist: subcommand denied', was(out, SUBDENIED), out)
  out = await send(bot, '/minecraft:gamemode creative')
  r.check('#N01 subcommand with namespace denied', was(out, SUBDENIED), out)
  out = await send(bot, '/execute run seed')
  r.check('#N04 /execute run of a blacklisted command denied', was(out, DENIED), out)
  out = await send(bot, '/minecraft:execute run minecraft:gamemode creative')
  r.check('#N01 #N04 /execute with namespaces denied', was(out, SUBDENIED), out)
  out = await send(bot, '/version')
  r.check('blacklist: other commands allowed', !was(out, DENIED) && !was(out, SUBDENIED), out)
  const timeTree = treeChildren(bot, 'time')
  if (target.isPaper && !v.startsWith('1.13')) {
    r.check('#N15 Paper: blacklisted subcommand removed from tree', timeTree && timeTree.length && !timeTree.includes('set') && timeTree.includes('query'), timeTree ? timeTree.join(',') : 'none')
  } else r.skip('#N15 tree pruning', 'needs the Paper event, tree shows ' + (timeTree || []).join(','))
  bot.quit()
  await sleep(500)
}

async function runBackend (name, target, r, config = whitelistConfig()) {
  const dir = prepareDir(name, config)
  serverProperties(dir, target.port)
  const jar = target.jar ? path.resolve(workDir, 'jars', target.jar) : await fetchJar(target.project, target.version)
  target.isPaper = target.project === 'paper' || target.project === 'folia'
  const server = new Server(name, jar, target.java, dir, ['nogui'])
  try {
    await server.waitFor(/Done \(/, 240000)
    r.check('plugin enabled with no errors', /CommandWhitelist.*(Enabling|enabled)/i.test(server.log) && !/Error occurred while enabling CommandWhitelist|Could not load 'plugins/.test(server.log))
    await backendSuite(r, server, target)
    const errors = server.log.split('\n').filter(l => /CommandWhitelist|commandwhitelist/.test(l) && /(ERROR|Exception|Caused by)/.test(l))
    r.check('no plugin errors in the log', errors.length === 0, errors.slice(0, 3).join(' / ').slice(0, 300))
  } finally {
    await server.stop()
    fs.writeFileSync(path.join(workDir, 'run', name + '.log'), server.log)
  }
}

async function runProxy (name, target, r) {
  // backend without the plugin, in offline mode behind the proxy
  const backendTarget = TARGETS[target.backend]
  const backendDir = path.join(workDir, 'run', name + '-backend')
  fs.rmSync(backendDir, { recursive: true, force: true })
  fs.mkdirSync(backendDir, { recursive: true })
  fs.writeFileSync(path.join(backendDir, 'eula.txt'), 'eula=true\n')
  serverProperties(backendDir, target.port + 100)
  const backendJar = await fetchJar(backendTarget.project, backendTarget.version)
  const backend = new Server(name + '-backend', backendJar, backendTarget.java, backendDir, ['nogui'])

  const proxyConfig = MESSAGES + `mode:\n  commands: BLACKLIST\n  subcommands: BLACKLIST\ngroups:\n  default:\n    commands: [bcw, vcw]\n    subcommands: ['seed secret']\n`
  const dir = prepareDir(name, proxyConfig)
  let proxy
  try {
    await backend.waitFor(/Done \(/, 240000)
    const jar = await fetchJar(target.proxy, target.version)
    if (target.proxy === 'velocity') {
      fs.writeFileSync(path.join(dir, 'velocity.toml'), `config-version = "2.7"\nbind = "127.0.0.1:${target.port}"\nonline-mode = false\nplayer-info-forwarding-mode = "none"\n[servers]\nlobby = "127.0.0.1:${target.port + 100}"\ntry = ["lobby"]\n[forced-hosts]\n`)
    } else {
      fs.writeFileSync(path.join(dir, 'config.yml'), `online_mode: false\nip_forward: false\nlisteners:\n- host: 127.0.0.1:${target.port}\n  query_port: ${target.port}\n  priorities: [lobby]\n  force_default_server: false\nservers:\n  lobby:\n    address: 127.0.0.1:${target.port + 100}\n    restricted: false\n    motd: test\nperms: {}\ngroups: {}\n`)
    }
    proxy = new Server(name, jar, target.java, dir)
    await proxy.waitFor(target.proxy === 'velocity' ? /Done \(/ : /Listening on/, 120000)
    await sleep(2000)
    r.check('plugin enabled on the proxy', !/Error|Exception/i.test(proxy.log.split('\n').filter(l => /commandwhitelist/i.test(l)).join('\n')), proxy.log.split('\n').filter(l => /commandwhitelist/i.test(l)).slice(0, 2).join(' / ').slice(0, 200))
    r.check('#N10 no false warning about message type', !/Invalid message type/.test(proxy.log))
    const bot = await connect('ProxyBot', target.port, target.client)
    await sleep(2500)
    const bungee = target.proxy === 'waterfall'
    const main = bungee ? 'bcw' : 'vcw'
    const cfg = path.join(dir, 'plugins', 'CommandWhitelist', 'config.yml')
    const HELP = 'CommandWhitelist by'
    let out = await send(bot, '/' + main)
    r.check('proxy: blacklisted proxy command not executed', !was(out, HELP) && (!bungee || was(out, DENIED)), out.slice(0, 100))
    out = await send(bot, '/' + main.toUpperCase())
    r.check('#01 proxy: other case not executed', !was(out, HELP), out.slice(0, 100))
    out = await send(bot, '/help')
    r.check('proxy: backend commands pass', !was(out, DENIED), out.slice(0, 80))
    const from = proxy.log.length
    proxy.command(main + ' remove default ' + main)
    await sleep(2500)
    r.check('#N05 console /cw remove saved', !new RegExp('- ' + main + '\\b').test(fs.readFileSync(cfg, 'utf8')))
    r.check('#N05 no %s in the answer', !proxy.log.slice(from).includes('%s'), proxy.log.slice(from).trim().slice(0, 120))
    out = await send(bot, '/' + main)
    r.check('Adventure works on this proxy: player receives the help', was(out, HELP), out.slice(0, 100))
    r.check('#N12 help shows the real command name', was(out, '/' + main + ' help'), out.slice(0, 100))
    bot.quit()
    await sleep(500)
    const errs = proxy.log.split('\n').filter(l => /commandwhitelist/i.test(l) && /(Exception|NoSuchMethod|NoClassDef)/.test(l))
    r.check('no plugin errors in the log', errs.length === 0, errs.slice(0, 2).join(' / ').slice(0, 300))
  } finally {
    if (proxy) {
      await proxy.stop(target.proxy === 'velocity' ? 'shutdown' : 'end')
      fs.writeFileSync(path.join(workDir, 'run', name + '.log'), proxy.log)
    }
    await backend.stop()
  }
}

async function main () {
  const reports = []
  for (const name of targetList.split(',')) {
    const target = TARGETS[name]
    console.log(`\n=== ${name}`)
    const r = new Report(name)
    reports.push(r)
    try {
      if (target.proxy) await runProxy(name, target, r)
      else await runBackend(name, target, r)
    } catch (e) {
      r.check('target completed', false, String(e.message || e).slice(0, 700))
    }
    fs.mkdirSync(path.join(workDir, 'results'), { recursive: true })
    fs.writeFileSync(path.join(workDir, 'results', name + '.json'), JSON.stringify(r.rows, null, 1))
  }
  console.log('\n=== SUMMARY')
  let failed = 0
  for (const r of reports) {
    const f = r.rows.filter(x => x.ok === false).length
    failed += f
    console.log(`${r.target}: ${r.rows.filter(x => x.ok).length} pass, ${f} fail, ${r.rows.filter(x => x.ok === null).length} skip`)
  }
  process.exit(failed ? 1 : 0)
}

main().catch(e => { console.error(e); process.exit(2) })
