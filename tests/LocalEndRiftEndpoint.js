'use strict'

const DEFAULT_LOCAL_HOST = '127.0.0.1'
const DEFAULT_LOCAL_PORT = '25566'

function resolveLocalEndRiftEndpoint(environment = process.env) {
  const host = environment.END_RIFT_BOT_HOST || DEFAULT_LOCAL_HOST
  if (host !== DEFAULT_LOCAL_HOST) {
    throw new Error(`Refused non-loopback End Rift bot host: ${host}`)
  }

  const rawPort = environment.END_RIFT_BOT_PORT || DEFAULT_LOCAL_PORT
  if (!/^\d+$/.test(rawPort)) {
    throw new Error(`Refused invalid End Rift bot port: ${rawPort}`)
  }
  const port = Number(rawPort)
  if (!Number.isInteger(port) || port < 1 || port > 65535) {
    throw new Error(`Refused invalid End Rift bot port: ${rawPort}`)
  }

  return Object.freeze({ host, port })
}

module.exports = { resolveLocalEndRiftEndpoint }
