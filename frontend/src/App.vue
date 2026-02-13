<template>
  <div class="page">
    <h1>实时录音 · 语音转文字</h1>
    <p class="hint">点击开始即开始识别，边说边出结果；停止后收最终结果</p>

    <div class="controls">
      <button
        type="button"
        class="btn-record"
        :class="{ recording: isRecording }"
        :disabled="uploading"
        @click="toggleRecord"
      >
        {{ isRecording ? '停止录音' : '开始录音' }}
      </button>
    </div>

    <div v-if="status" class="status" :class="statusType">{{ status }}</div>
    <div v-if="partialText" class="result partial">
      <div class="result-label">识别中：</div>
      <div class="result-text">{{ partialText }}</div>
    </div>
    <div v-if="resultText" class="result">
      <div class="result-label">识别结果：</div>
      <div class="result-text">{{ resultText }}</div>
    </div>
  </div>
</template>

<script setup>
import { ref, onBeforeUnmount, onUnmounted } from 'vue'

const isRecording = ref(false)
const uploading = ref(false)
const status = ref('')
const statusType = ref('info') // info | success | error
const partialText = ref('')
const resultText = ref('')

let ws = null
let audioContext = null
let processor = null
let source = null
let closingIntentionally = false

const WS_URL = 'ws://localhost:8080/ws/asr'
const TARGET_SAMPLE_RATE = 16000

function setStatus(msg, type = 'info') {
  status.value = msg
  statusType.value = type
}

function toggleRecord() {
  if (isRecording.value) {
    stopRecording()
  } else {
    startRecording()
  }
}

function floatTo16BitPCM(float32Array) {
  const int16 = new Int16Array(float32Array.length)
  for (let i = 0; i < float32Array.length; i++) {
    const s = Math.max(-32768, Math.min(32767, Math.floor(float32Array[i] * 32768)))
    int16[i] = s
  }
  return int16.buffer
}

function downsample(inputRate, outputRate, float32Array) {
  const ratio = inputRate / outputRate
  const outLength = Math.round(float32Array.length / ratio)
  const result = new Float32Array(outLength)
  for (let i = 0; i < outLength; i++) {
    const srcIndex = i * ratio
    const srcIndexFloor = Math.floor(srcIndex)
    const srcIndexCeil = Math.min(srcIndexFloor + 1, float32Array.length - 1)
    const t = srcIndex - srcIndexFloor
    result[i] = float32Array[srcIndexFloor] * (1 - t) + float32Array[srcIndexCeil] * t
  }
  return result
}

async function startRecording() {
  try {
    const stream = await navigator.mediaDevices.getUserMedia({ audio: true })
    ws = new WebSocket(WS_URL)

    ws.onmessage = (e) => {
      try {
        const data = JSON.parse(e.data)
        if (data.error) {
          setStatus(data.error, 'error')
          partialText.value = ''
        } else if (data.type === 'partial' && data.text) {
          partialText.value = data.text
        } else if (data.type === 'final' || data.text) {
          const text = data.text != null ? data.text : ''
          resultText.value = text
          partialText.value = ''
          if (data.type === 'final' || !isRecording.value) {
            setStatus('识别完成', 'success')
            uploading.value = false
          }
        }
      } catch (_) {}
    }
    ws.onerror = () => setStatus('WebSocket 错误', 'error')
    ws.onclose = () => {
      if (!closingIntentionally && isRecording.value) {
        setStatus('连接已断开，请重新开始录音', 'error')
      }
    }

    await new Promise((resolve, reject) => {
      ws.onopen = resolve
      ws.onerror = () => reject(new Error('连接失败'))
    })

    audioContext = new (window.AudioContext || window.webkitAudioContext)({ sampleRate: 48000 })
    source = audioContext.createMediaStreamSource(stream)
    const bufferSize = 4096
    processor = audioContext.createScriptProcessor(bufferSize, 1, 1)
    const inputRate = audioContext.sampleRate

    processor.onaudioprocess = (e) => {
      if (!ws || ws.readyState !== WebSocket.OPEN) return
      try {
        const input = e.inputBuffer.getChannelData(0)
        const down = downsample(inputRate, TARGET_SAMPLE_RATE, input)
        const pcm = floatTo16BitPCM(down)
        ws.send(pcm)
      } catch (err) {
        console.warn('发送 PCM 失败', err)
      }
    }
    source.connect(processor)
    processor.connect(audioContext.destination)

    isRecording.value = true
    resultText.value = ''
    partialText.value = ''
    setStatus('正在录音，实时识别中…', 'info')
  } catch (e) {
    setStatus('无法访问麦克风或连接失败: ' + (e?.message || e), 'error')
    if (ws) {
      closingIntentionally = true
      ws.close()
      ws = null
    }
  }
}

function stopRecording() {
  if (!isRecording.value) return
  isRecording.value = false
  if (processor && source) {
    try {
      source.disconnect()
      processor.disconnect()
    } catch (_) {}
    processor = null
    source = null
  }
  if (audioContext) {
    audioContext.close().catch(() => {})
    audioContext = null
  }
  if (ws && ws.readyState === WebSocket.OPEN) {
    ws.send('end')
    uploading.value = true
    setStatus('已停止，等待最终结果…', 'info')
    const t = setTimeout(() => {
      uploading.value = false
      closingIntentionally = true
      try { ws.close() } catch (_) {}
      ws = null
    }, 5000)
    ws.onclose = () => {
      clearTimeout(t)
      uploading.value = false
      ws = null
    }
  }
}

onBeforeUnmount(() => {
  closingIntentionally = true
})

onUnmounted(() => {
  if (processor && source) {
    try { source.disconnect(); processor.disconnect() } catch (_) {}
  }
  if (audioContext) audioContext.close().catch(() => {})
  if (ws && ws.readyState === WebSocket.OPEN) ws.close()
})
</script>

<style>
* {
  box-sizing: border-box;
}
body {
  margin: 0;
  font-family: 'Segoe UI', system-ui, sans-serif;
  background: linear-gradient(135deg, #1a1a2e 0%, #16213e 100%);
  min-height: 100vh;
  color: #e8e8e8;
}
#app {
  max-width: 560px;
  margin: 0 auto;
  padding: 2rem 1rem;
}
.page {
  text-align: center;
}
h1 {
  font-size: 1.75rem;
  font-weight: 600;
  margin-bottom: 0.5rem;
}
.hint {
  color: #a0a0b0;
  font-size: 0.95rem;
  margin-bottom: 2rem;
}
.controls {
  margin-bottom: 1.5rem;
}
.btn-record {
  padding: 1rem 2rem;
  font-size: 1.1rem;
  border: none;
  border-radius: 999px;
  cursor: pointer;
  background: #0f3460;
  color: #fff;
  transition: background 0.2s, transform 0.1s;
}
.btn-record:hover:not(:disabled) {
  background: #1a4a7a;
}
.btn-record:active:not(:disabled) {
  transform: scale(0.98);
}
.btn-record.recording {
  background: #c23a3a;
  animation: pulse 1.2s ease-in-out infinite;
}
.btn-record:disabled {
  opacity: 0.7;
  cursor: not-allowed;
}
@keyframes pulse {
  50% { opacity: 0.85; }
}
.status {
  padding: 0.6rem 1rem;
  border-radius: 8px;
  margin-bottom: 1rem;
  font-size: 0.95rem;
}
.status.info {
  background: rgba(100, 150, 255, 0.2);
  color: #a0c8ff;
}
.status.success {
  background: rgba(80, 180, 120, 0.2);
  color: #90e0b0;
}
.status.error {
  background: rgba(200, 80, 80, 0.2);
  color: #f0a0a0;
}
.result {
  text-align: left;
  background: rgba(255, 255, 255, 0.06);
  border-radius: 12px;
  padding: 1.25rem;
  border: 1px solid rgba(255, 255, 255, 0.08);
}
.result.partial {
  border-color: rgba(100, 200, 255, 0.3);
}
.result-label {
  font-size: 0.85rem;
  color: #a0a0b0;
  margin-bottom: 0.5rem;
}
.result-text {
  font-size: 1rem;
  line-height: 1.5;
  white-space: pre-wrap;
  word-break: break-word;
}
</style>
