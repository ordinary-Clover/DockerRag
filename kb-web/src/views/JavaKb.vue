<template>
  <div class="java-kb">
    <h2>【Docker版】Java本地知识库</h2>
    <el-upload
        action=""
        :http-request="uploadFile"
    >
      <el-button type="primary">上传文档 pdf/txt/docx</el-button>
    </el-upload>
    <div style="margin:20px 0;"></div>
    <el-input v-model="question" placeholder="请输入问题"/>
    <el-button type="success" @click="sendChat">提问</el-button>
    <el-card style="margin-top:20px;">
      <pre>{{ answer }}</pre>
    </el-card>
  </div>
</template>

<script setup>
import { ref, getCurrentInstance } from 'vue'
const question = ref('')
const answer = ref('')
const { proxy } = getCurrentInstance()

async function uploadFile(params){
  const formData = new FormData()
  formData.append('file', params.file)
  const res = await proxy.$axios.post('/upload', formData)
  alert(res.data)
}

async function sendChat(){
  if(!question.value.trim()){
    alert('请输入问题')
    return
  }

  answer.value = "思考中..."

  try{
    const response = await fetch(`http://localhost:8080/api/kb/chat/stream?question=${encodeURIComponent(question.value)}`)

    if(!response.ok){
      throw new Error(`HTTP error! status: ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let result = ''
    let buffer = ''

    while(true){
      const {done, value} = await reader.read()
      if(done) break

      buffer += decoder.decode(value, {stream: true})
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for(const line of lines){
        const trimmedLine = line.trim()
        if(trimmedLine.startsWith('data:')){
          const content = trimmedLine.substring(5).trim()
          if(content && content !== '[DONE]'){
            result += content
            answer.value = result
          }
        }
      }
    }

    if(buffer.trim().startsWith('data:')){
      const content = buffer.trim().substring(5).trim()
      if(content && content !== '[DONE]'){
        result += content
        answer.value = result
      }
    }
  }catch(error){
    console.error('请求失败:', error)
    answer.value = '请求失败: ' + error.message
  }
}
</script>
