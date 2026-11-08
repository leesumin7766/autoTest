import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// 이 PC 처럼 운영 구성(docker-compose.prod.yml)으로 서버를 띄운 경우의 화면 개발용 설정.
// 운영 구성은 api-gateway(8080)를 호스트에 열지 않으므로 /api 를 Caddy(https://localhost:18443)로 보낸다.
// Caddy 는 사내 CA(tls internal) 인증서를 쓰므로, 이 PC 의 localhost 연결에 한해 인증서 검증을 생략한다.
export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    strictPort: true,
    proxy: {
      '/api': { target: 'https://localhost:18443', changeOrigin: true, secure: false },
    },
  },
})
