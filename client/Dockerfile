# Use a node image as the base image
FROM node:22-alpine AS build
ENV REACT_APP_SERVER_BASE_URL=https://i8a705.p.ssafy.io/api
# Set the working directory
WORKDIR /app
# Copy the package.json and package-lock.json files to the working directory
COPY client/package*.json ./
# Install the dependencies
RUN npm ci
# Copy the rest of the source code to the working directory
COPY client/. .
# Build the React app
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
# Copy the Nginx configuration file to the appropriate location
COPY client/nginx/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 3000
# Start Nginx
CMD ["nginx", "-g", "daemon off;"]
