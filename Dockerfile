FROM theasp/clojurescript-nodejs:alpine

COPY . .

RUN npm i && npm run build

EXPOSE 3000
CMD ["npm", "run", "serve"]
