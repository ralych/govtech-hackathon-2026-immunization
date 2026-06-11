FROM nginx:alpine

# Copy all static assets into the default Nginx html serving directory
COPY . /usr/share/nginx/html

# Configure Nginx to serve producer.html as the index file instead of index.html
RUN sed -i 's/index  index.html index.htm;/index  producer.html index.html index.htm;/g' /etc/nginx/conf.d/default.conf

EXPOSE 80
