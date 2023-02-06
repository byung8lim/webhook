###############################################################################
##  Filename : setup.sh                                                      ##
##  Author   : LBM                                                           ##
##  Date     : 2023-02-03                                                    ##
## A script of the example for Kubernetes Authentification, Authorization,   ##
## and Admission Webhook.                                                    ##
###############################################################################
#!/bin/bash

NAMESPACE="examples"
unset REGISTRY
unset IMAGE_URL

BASE=`pwd`
NGINX_HOME=$BASE/nginx
NGINX_CONF=$BASE/nginx/conf.d
NGINX_TMPL=$BASE/nginx/templates
unset METHOD
NIC=$(ip addr | awk '$2 ~ /^en.*:/{idx=index($2,":");print substr($2,1,idx-1)}')
HOSTIP=$(ip addr | grep $NIC | awk '/inet /{idx=index($2, "/");print substr($2,1, idx-1)}')
HOSTNAME="webhook.wbl.com"

function usage() {
  echo "###########################################################################"
  echo "#*                                                                       *#"
  echo "#*    setup.sh -command=[build|clean] \                                  *#"
  echo "#*        -namespace=[NAMESPACE] \                                       *#"
  echo "#*        -registry=[DOCKER_REGISTRY]                                    *#"
  echo "#*                                                                       *#"
  echo "###########################################################################"
}

function cleanup() {
    echo "Now cleanning ..."
    echo "kubectl delete ns $NAMESPACE"
    kubectl delete namespace $NAMESPACE 
	
    echo "IMG_ID=\$(docker images | awk '/webhook/{print \$3}')"
    IMG_ID=$(docker images | awk '/webhook/{print $3}')
    echo "docker rmi $IMG_ID"
    docker rmi $IMG_ID
	
    if [ -d $BASE/target ];then
        echo "rm -rf $BASE/target"
        rm -rf $BASE/target
    fi
	
    if [ -d $BASE/templates ];then
        echo "rm -rf $BASE/templates"
        rm -rf $BASE/templates
    fi
	
    if [ -d $NGINX_HOME ];then
        echo "rm -rf $NGINX_HOME/*"
        rm -rf $NGINX_HOME
    fi

    echo "rm -f $BASE/webmutate.yaml"
    rm -f $BASE/webmutate.yaml
    echo "rm -f $BASE/validatingwebhook.yaml"
    rm -f $BASE/validatingwebhook.yaml
    echo "sudo find $BASE -name 'webauthen.yaml' -exec rm -f {} \;"
    sudo find $BASE -name 'webauthen.yaml' -exec rm -f {} \;
    echo "sudo find $BASE -name 'webauthoz.yaml' -exec rm -f {} \;"
    sudo find $BASE -name 'webauthoz.yaml' -exec rm -f {} \;
    sudo rm -f /etc/kubernetes/pki/webhook-CA.pem
    echo "sudo rm -f /etc/kubernetes/pki/webhook-CA.pem"
}

function clean_tls() {
    echo "rm -f $NGINX_CONF/rootCA-key.pem"
    rm -f $NGINX_CONF/rootCA-key.pem
    echo "rm -f $NGINX_CONF/rootCA.pem"
    rm -f $NGINX_CONF/rootCA.pem
    echo "rm -f $NGINX_CONF/webhook-key.pem"
    rm -f $NGINX_CONF/webhook-key.pem
    echo "rm -f $NGINX_CONF/webhook.pem"
    rm -f $NGINX_CONF/webhook.pem
    echo "rm -f $NGINX_CONF/rootCA-config.json"
    rm -f $NGINX_CONF/rootCA-config.json
    echo "rm -f $NGINX_CONF/rootCA-csr.json"
    rm -f $NGINX_CONF/rootCA-csr.json
    echo "rm -f $NGINX_CONF/server-csr.json"
    rm -f $NGINX_CONF/server-csr.json
}

function create_dirs() {
    if [ -d $BASE/templates ];then
        ls -al $BASE/templates
	rm -rf $BASE/templates
    fi
    echo "mkdir -p $BASE/templates"
    mkdir -p $BASE/templates
    
    if [ -d $NGINX_CONF ];then
        ls -al $NGINX_CONF
	rm -rf $NGINX_CONF
    fi
    echo "mkdir -p $NGINX_CONF"
    mkdir -p $NGINX_CONF
    
    if [ -d $NGINX_TMPL ];then
        ls -al $NGINX_TMPL
	rm -rf $NGINX_TMPL
    fi
    echo "mkdir -p $NGINX_TMPL"
    mkdir -p $NGINX_TMPL
    
}
############################### Begin of Build Webhook Source ###########################
function build_src() {
    echo "Building Webhook ..."
    mvn clean package -DskipTests=true

    TARBALL=$(find . -name 'webhook*.jar' -type f)
    echo "TARBALL : ${TARBALL}"
    if [ -z "$TARBALL" ];then
        echo "Building src may be failed "
        exit 1
    fi
}
############################### End of Build Webhook Source ###########################

############################### Begin of Build Webhook Template ###########################
function generate_webhook_template() {
    cd $BASE/templates
    echo "Generating Webhook Deployment"
    cat > deployment.yaml <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: webhook
  name: webhook
spec:
  replicas: 1
  selector:
    matchLabels:
      app: webhook
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: webhook
    spec:
      containers:
      - image: ${IMAGE_URL}
        name: webhook
        volumeMounts:
        - mountPath: /etc/localtime
          name: tz-seoul
        resources: {}
      volumes:
      - hostPath:
          path: /usr/share/zoneinfo/Asia/Seoul
          type: ""
        name: tz-seoul
status: {}
---
EOF
####################### Webhook service
    echo "Generating Webhook service"
    cat > service.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  creationTimestamp: null
  labels:
    app: webhook
  name: webhook
spec:
  ports:
  - name: http-svc
    port: 8080
    protocol: TCP
    targetPort: 8080
  selector:
    app: webhook
  type: ClusterIP
status:
  loadBalancer: {}
---
EOF
}
############################### End of Build Webhook Template ###########################

############################### Begin of Build and Push Webhook Image ###########################
function build_and_push_webhook_image() {
    echo "Build webhook docker image"
    IMAGE_URL="$REGISTRY/$NAMESPACE/webhook:1.0.0"
    echo "docker build -t $IMAGE_URL ."
    docker build -t $REGISTRY/$NAMESPACE/webhook:1.0.0 .
    if [ $? -gt 0 ];then
      echo "Failed to build docker image"
      exit 1
    fi
    
    echo "docker push $IMAGE_URL"
    docker push $IMAGE_URL
    if [ $? -gt 0 ];then
      echo "Failed to push image"
      exit 1
    fi
}
############################### End of Build and Push Webhook Image ###########################

############################### Beginning of Generate TLS Certs ###########################
function generate_tls_certs() {
    echo "Generating TLS"
    echo "cd $NGINX_CONF"
    cd $NGINX_CONF
    cat > rootCA-config.json <<EOF
{
  "signing": {
    "default": {
      "expiry": "8760h"
    },
    "profiles": {
      "root-ca": {
        "usages": ["signing", "key encipherment", "server auth", "client auth"],
        "expiry": "8760h"
      }
    }
  }
}
EOF

    cat > rootCA-csr.json <<EOF
{
  "CN": "rootCA",
  "key": {
    "algo": "rsa",
    "size": 2048
  },
  "names": [
    {
      "O": "Kubernetes"
    }
  ]
}
EOF
#####################  generate new rootCA-csr
    cfssl gencert -initca rootCA-csr.json | cfssljson -bare rootCA

    cat > server-csr.json <<EOF
{
  "CN": "$HOSTIP",
  "hosts": [
    "webhook.wbl.com",
    "nginx.examples.svc",
    "$HOSTIP"
  ],
  "key": {
    "algo": "rsa",
    "size": 2048
  },
  "names": [
    {
      "O": "server-group"
    }
  ]
}
EOF
####################  gencert for certs
    cfssl gencert \
      -ca=rootCA.pem \
      -ca-key=rootCA-key.pem \
      -config=rootCA-config.json \
      -profile=root-ca \
      server-csr.json | cfssljson -bare webhook
    echo "sudo cp $NGINX_CONF/rootCA.pem /etc/kubernetes/pki/webhook-CA.pem"
    sudo cp $NGINX_CONF/rootCA.pem /etc/kubernetes/pki/webhook-CA.pem
}
############################### End of Generate TLS Certs ###########################

############################### Beginning of Generate Webauthen ###########################
function generate_webauthen_yaml() {
    cd $BASE
    echo "Generation Authentification Webhook config"
    cat > webauthen.yaml <<EOF
apiVersion: v1
kind: Config
clusters:
- name: flask-auth
  cluster:
    server: http://$HOSTIP:32088/authentication
users:
- name: kube-apiserver
contexts:
- context:
    cluster: flask-auth
    user: kube-apiserver
  name: auth
current-context: auth
EOF
    echo "sudo cp webauthen.yaml /etc/kubernetes/pki/"
    sudo cp webauthen.yaml /etc/kubernetes/pki/
}
############################### End of Generate Webauthen ###########################

############################### Beginning of Generate Webauthorz ###########################
function generate_webauthorz_yaml() {
    cd $BASE
    echo "Generating Authorization Wehbook config"
    cat > webauthoz.yaml <<EOF
apiVersion: v1
kind: Config
clusters:
  - name: authorization-webhook
    cluster:
      certificate-authority: /etc/kubernetes/pki/webhook-CA.pem
      server: https://$HOSTIP:32089/authorization
users:
  - name: kube-apiserver
    user:
      client-certificate: /etc/kubernetes/pki/apiserver-kubelet-client.crt
      client-key: /etc/kubernetes/pki/apiserver-kubelet-client.key
current-context: webhook
contexts:
- context:
    cluster: authorization-webhook
    user: kube-apiserver
  name: webhook
EOF

    echo "sudo cp webauthoz.yaml /etc/kubernetes/pki/"
    sudo cp webauthoz.yaml /etc/kubernetes/pki/
}
############################### End of Generate Webauthorz ###########################

############################### Beginning of Generate Webmutate ###########################
function generate_webmutate_yaml() {
    cd $BASE
    echo "Generating Authorization Wehbook config"
    cat > webmutate.yaml <<EOF
apiVersion: admissionregistration.k8s.io/v1
kind: MutatingWebhookConfiguration
metadata:
  name: nginx-injector-webhook
webhooks:
- admissionReviewVersions:
  - v1
  - v1beta1
  clientConfig:
    caBundle: $(cat $NGINX_CONF/rootCA.pem | base64 | tr -d '\n')
    service:
      name: nginx
      namespace: examples
      path: "/mutate"
      port: 8443
  failurePolicy: Fail
  matchPolicy: Equivalent
  name: nginx.examples.svc
  namespaceSelector:
    matchLabels:
      nginx-injector: enabled
  objectSelector: {}
  reinvocationPolicy: Never
  rules:
  - apiGroups:
    - ""
    apiVersions:
    - v1
    operations:
    - CREATE
    - UPDATE
    resources:
    - pods
    scope: '*'
  sideEffects: None
  timeoutSeconds: 10
---
EOF
}
############################### End of Generate Webauthorz ###########################

function generate_validatingwebhook() {
    cd $BASE
    cat > validatingwebhook.yaml <<EOF
apiVersion: admissionregistration.k8s.io/v1
kind: ValidatingWebhookConfiguration
metadata:
  name: admission-webhook-validator
webhooks:
- name: nginx.examples.svc
  admissionReviewVersions:
  - v1
  - v1beta1
  clientConfig:
    caBundle: $(cat $NGINX_CONF/rootCA.pem | base64 | tr -d '\n')
    service:
      name: nginx
      namespace: examples
      path: "/admissionreview"
      port: 8443
  rules:
  - apiGroups:
    - ""
    apiVersions:
    - v1
    operations:
    - CREATE
    - UPDATE
    resources:
    - pods
    scope: '*'
  sideEffects: None
  timeoutSeconds: 10
---
EOF
}
############################### Begin of nginx template ###########################
function generate_nginx_template() {
    cd $NGINX_TMPL
################################## Nginx Deployment ##############################
    echo "Generating Nginx Deployment"
    cat > deployment.yaml <<EOF
apiVersion: apps/v1
kind: Deployment
metadata:
  creationTimestamp: null
  labels:
    app: nginx
  name: nginx
spec:
  replicas: 1
  selector:
    matchLabels:
      app: nginx
  strategy: {}
  template:
    metadata:
      creationTimestamp: null
      labels:
        app: nginx
    spec:
      containers:
      - image: nginx:latest
        name: nginx
        volumeMounts:
        - mountPath: /etc/nginx/conf.d
          name: nginx-conf
        resources: {}
      volumes:
      - hostPath:
          path: $NGINX_CONF
          type: ""
        name: nginx-conf
status: {}
---
EOF

################################## Nginx Service ##############################
    echo "Generating Nginx Service"
    cat > service.yaml <<EOF
apiVersion: v1
kind: Service
metadata:
  creationTimestamp: null
  labels:
    app: nginx
  name: nginx
spec:
  ports:
  - name: port-1
    port: 80
    protocol: TCP
    targetPort: 80
  - name: port-2
    port: 443
    protocol: TCP
    targetPort: 443
  - name: port-3
    port: 8443
    protocol: TCP
    targetPort: 8443
  selector:
    app: nginx
  sessionAffinity: None
  type: ClusterIP
status: {}
---
EOF
}
############################### End of nginx template ###########################

############################### Begin of nginx conf ###########################
function generate_nginx_conf() {
    cd $NGINX_CONF
    echo "Generating nginx conf"
    cat > default.conf <<EOF
server {
    listen 443 ssl;
    ssl_certificate      /etc/nginx/conf.d/webhook.pem;
    ssl_certificate_key  /etc/nginx/conf.d/webhook-key.pem;

    ssl_verify_client on;
    ssl_client_certificate /etc/nginx/conf.d/k8s-rootCA.pem;
    location /authorization {
            proxy_pass http://webhook:8080/authoz;
            proxy_http_version 1.1;
            proxy_set_header Upgrade \$http_upgrade;
            proxy_set_header Connection 'upgrade';
            proxy_set_header Host \$host;
            proxy_set_header  Authorization \$http_authorization;
            proxy_pass_header Authorization;
            proxy_set_header X-Read-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_cache_bypass \$http_upgrade;
    }
}

server {
    listen 8443 ssl;
    ssl_certificate      /etc/nginx/conf.d/webhook.pem;
    ssl_certificate_key  /etc/nginx/conf.d/webhook-key.pem;


    location /admissionreview {
            proxy_pass http://webhook:8080/admission;
            proxy_http_version 1.1;
            proxy_set_header Upgrade \$http_upgrade;
            proxy_set_header Connection 'upgrade';
            proxy_set_header Host \$host;
            proxy_set_header  Authorization \$http_authorization;
            proxy_pass_header Authorization;
            proxy_set_header X-Read-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_cache_bypass \$http_upgrade;
    }

    location /mutate {
            proxy_pass http://webhook:8080/mutate;
            proxy_http_version 1.1;
            proxy_set_header Upgrade \$http_upgrade;
            proxy_set_header Connection 'upgrade';
            proxy_set_header Host \$host;
            proxy_set_header  Authorization \$http_authorization;
            proxy_pass_header Authorization;
            proxy_set_header X-Read-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_cache_bypass \$http_upgrade;
    }
}

server {
    listen 80;
    location / {
        root  /usr/share/nginx/html;
        index index.html index.htm;
}
    location /authentication {
            proxy_pass http://webhook:8080/authen;
            proxy_http_version 1.1;
            proxy_set_header Upgrade \$http_upgrade;
            proxy_set_header Connection 'upgrade';
            proxy_set_header Host \$host;
            proxy_set_header  Authorization \$http_authorization;
            proxy_pass_header Authorization;
            proxy_set_header X-Read-IP \$remote_addr;
            proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
            proxy_cache_bypass \$http_upgrade;
    }
}
EOF
    echo "sudo cp /etc/kubernetes/pki/ca.crt $NGINX_CONF/k8s-rootCA.pem"
    sudo cp /etc/kubernetes/pki/ca.crt $NGINX_CONF/k8s-rootCA.pem
    sudo chown $USER:$GROUP $NGINX_CONF/k8s-rootCA.pem
}

function profile() {
echo "#*******************************************************************************************#"
echo "#*                     You should change kube-apiserver config                             *#"
echo "#* 1) webhook for authentification                                                         *#"
echo "#* sudo vi /etc/kubernetes/manifests/kube-apiserver.yaml                                   *#"
echo "#*  - --authentication-token-webhook-config-file=/etc/kubernetes/pki/webauthen.yaml (+)    *#"
echo "#* 2) webhook for authorization                                                            *#"
echo "#* sudo vi /etc/kubernetes/manifests/kube-apiserver.yaml                                   *#"
echo "#*  - --authorization-mode=Node,RBAC                                                       *#"
echo "#*       ==>  - --authorization-mode=Webhook,RBAC                                          *#"
echo "#*  - --authorization-webhook-config-file=/etc/kubernetes/pki/webauthoz.yaml (+)           *#"
echo "#* 3) webhook for mutating & validating admission                                          *#"
echo "#*   kubectl apply -f validatingwebhook.yaml                                               *#"
echo "#*   kubectl apply -f webmutate.yaml                                                       *#"
echo "#*   kubectl create namespace test-ns                                                      *#"
echo "#*   kubectl label namespace test-ns nginx-injector=enabled                                *#"
echo "#*   kubectl -n test-ns run alpine --image=alpine                                          *#"
echo "#*                                                                                         *#"
echo "#***********************      Enjoy Kubernetes Webhook examples          *******************#"

}
############################### End of nginx conf ###########################


##################################### Checking Parameters ###################################
if [ $# -lt 1 ];then
  usage
  exit 1
else
  for arg in $*;do
    case $arg in
      --registry*|--REGISTRY*|--Registry*|-R*) REGISTRY=$(echo $arg | awk -F '=' '{print $2}') ;;
      --command*|-C*) METHOD=$(echo $arg | awk -F '=' '{print $2}') ;;
      *) ;;
    esac
  done
fi

if [ -z "$NAMESPACE" ];then
  echo "You should get namespace as parameter"
  usage
  exit 1
fi


if [ "clean" == "$METHOD" ];then
    cleanup
    exit 0
elif [ "build" == "$METHOD" ];then
    echo "Now build..........."
elif [ "dir" == "$METHOD" ];then
    create_dirs
    exit 0
elif [ "tls" == "$METHOD" ];then
    clean_tls
    generate_tls_certs
    exit 0
elif [ "mutate" == "$METHOD" ];then
    find . -name 'webmutate.yaml' -exec rm {} \;
    generate_webmutate_yaml
    exit 0
elif [ "profile" == "$METHOD" ];then
    profile
    exit 0
else
    echo "Unknown command : ${METHOD}"
    exit 0
fi

echo "NAMESPACE: ${NAMESPACE}"
echo "REGISTRY: ${REGISTRY}"

############# Building webhook source
############# Building webhook source
create_dirs
build_src
build_and_push_webhook_image
generate_webhook_template

cd $BASE/templates
echo "kubectl create ns $NAMESPACE"
kubectl create ns $NAMESPACE
echo "kubectl -n $NAMESPACE apply -f ."
kubectl -n $NAMESPACE apply -f .

############# Preparation for nginx
generate_tls_certs
generate_webauthen_yaml
generate_webauthorz_yaml
generate_webmutate_yaml
generate_validatingwebhook
generate_nginx_conf
generate_nginx_template
cd $NGINX_TMPL
echo "kubectl -n $NAMESPACE apply -f ."
kubectl -n $NAMESPACE apply -f .

profile
