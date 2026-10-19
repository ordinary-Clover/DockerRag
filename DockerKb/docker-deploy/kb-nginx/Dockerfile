# 使用nginx基础镜像
FROM nginx:alpine
# 删除nginx默认页面
RUN rm -rf /usr/share/nginx/html/*
# 把dist静态文件复制到nginx网页目录
#COPY ./docker-deploy/kb-nginx/dist/ /usr/share/nginx/html
COPY ./dist/ /usr/share/nginx/html/
# 复制nginx配置（处理前端路由history模式，必须）
COPY ./nginx/conf/nginx.conf /etc/nginx/nginx.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]