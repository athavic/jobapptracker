#!/usr/bin/env node
/**
 * Frees the dev ports when a runner died without cleaning up after itself.
 *
 * Ctrl-C in a normal terminal usually works fine - concurrently forwards the
 * signal and both servers shut down. This is for the cases where that does not
 * happen: you closed the terminal window, hit stop in the IDE, or something
 * crashed. On Windows the child java and node processes outlive their parent
 * and keep squatting on 8080 and 5173, so the next `npm run dev` fails with
 * "Port 5173 is already in use".
 */
import { execSync } from 'node:child_process'

const PORTS = [
  { port: 8080, name: 'api' },
  { port: 5173, name: 'web' },
]

const isWindows = process.platform === 'win32'

function pidsOnPort(port) {
  try {
    if (isWindows) {
      const out = execSync('netstat -ano', { encoding: 'utf8' })
      const pids = out
        .split('\n')
        .filter((line) => line.includes('LISTENING') && new RegExp(`:${port}\\s`).test(line))
        .map((line) => line.trim().split(/\s+/).pop())
        .filter((pid) => pid && pid !== '0')
      return [...new Set(pids)]
    }

    const out = execSync(`lsof -ti tcp:${port} -sTCP:LISTEN`, { encoding: 'utf8' })
    return out.split('\n').map((s) => s.trim()).filter(Boolean)
  } catch {
    // netstat and lsof exit non-zero when nothing matches.
    return []
  }
}

function kill(pid) {
  try {
    // /T kills the whole tree - maven spawns java as a child, and killing only
    // the parent is exactly how the orphan got there in the first place.
    execSync(isWindows ? `taskkill /PID ${pid} /T /F` : `kill -9 ${pid}`, { stdio: 'ignore' })
  } catch {
    // Deliberately ignored. A non-zero exit usually just means the process
    // already died between the scan and the kill, so it says nothing useful
    // about whether the port is free. The check below is the real answer.
  }
}

let cleaned = 0
let stuck = 0

for (const { port, name } of PORTS) {
  const label = `  ${name.padEnd(3)} :${port}`
  const pids = pidsOnPort(port)

  if (pids.length === 0) {
    console.log(`${label}  already free`)
    continue
  }

  pids.forEach(kill)

  // Trust the port, not the exit code.
  if (pidsOnPort(port).length === 0) {
    console.log(`${label}  freed (was pid ${pids.join(', ')})`)
    cleaned++
  } else {
    console.log(`${label}  STILL HELD by pid ${pidsOnPort(port).join(', ')} - kill it manually`)
    stuck++
  }
}

if (stuck > 0) {
  console.log('\nSome ports could not be freed. They may belong to another user or a service.')
  process.exitCode = 1
} else {
  console.log(
    cleaned > 0
      ? `\nFreed ${cleaned} port${cleaned === 1 ? '' : 's'}. Postgres is untouched - use "npm run db:down" for that.`
      : '\nNothing to clean up.',
  )
}
