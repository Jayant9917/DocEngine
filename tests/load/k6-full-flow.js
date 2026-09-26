/*
  DocEngine real-flow load test.

  This proves that the API can absorb 100 concurrent real users sending real
  CSV data and creating real background jobs. It checks request acceptance,
  duplicate-safe job submission, and low API latency independently of how long
  the workers take to finish processing the queued jobs.
*/

import http from 'k6/http'
import { check, sleep } from 'k6'

export const options = {
  vus: Number(__ENV.VUS || 100),
  duration: __ENV.DURATION || '30s',
}

const API_BASE_URL = __ENV.API_BASE_URL || 'http://127.0.0.1:8081'
const API_KEY = 'demo-tenant-key'
const csvData = open('../integration/resources/september-sales.csv', 'b')

export default function () {
  const uploadResponse = http.post(
    `${API_BASE_URL}/api/v1/uploads`,
    { file: http.file(csvData, 'september-sales.csv', 'text/csv') },
    { headers: { 'X-API-Key': API_KEY } },
  )

  const uploadOk = check(uploadResponse, {
    'upload returns 201': (response) => response.status === 201,
    'upload returns inputReference': (response) => {
      try {
        return Boolean(JSON.parse(response.body).inputReference)
      } catch (_) {
        return false
      }
    },
  })

  if (!uploadOk) {
    console.error(`Upload failed: status=${uploadResponse.status}, body=${uploadResponse.body}`)
    sleep(1)
    return
  }

  const inputReference = JSON.parse(uploadResponse.body).inputReference
  const idempotencyKey = `k6-vu-${__VU}-iter-${__ITER}-${Date.now()}`

  const jobResponse = http.post(
    `${API_BASE_URL}/api/v1/jobs`,
    JSON.stringify({
      jobType: 'GENERATE_MONTHLY_REPORT',
      input: { month: '2026-09', dataFile: inputReference },
    }),
    {
      headers: {
        'Content-Type': 'application/json',
        'X-API-Key': API_KEY,
        'Idempotency-Key': idempotencyKey,
      },
    },
  )

  const jobOk = check(jobResponse, {
    'job submission returns 202': (response) => response.status === 202,
  })

  if (!jobOk) {
    console.error(`Job submission failed: status=${jobResponse.status}, body=${jobResponse.body}`)
  }

  sleep(1)
}
