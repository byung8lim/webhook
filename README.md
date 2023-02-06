# webhook

 1) webhook for authentification                                                         
 sudo vi /etc/kubernetes/manifests/kube-apiserver.yaml                                   
  - --authentication-token-webhook-config-file=/etc/kubernetes/pki/webauthen.yaml (+)    
 2) webhook for authorization                                                            
 sudo vi /etc/kubernetes/manifests/kube-apiserver.yaml                                   
  - --authorization-mode=Node,RBAC                                                       
       ==>  - --authorization-mode=Webhook,RBAC                                          
  - --authorization-webhook-config-file=/etc/kubernetes/pki/webauthoz.yaml (+)           
 3) webhook for mutating & validating admission                                          
   kubectl apply -f validatingwebhook.yaml                                               
   kubectl apply -f webmutate.yaml                                                       
   kubectl create namespace test-ns                                                      
   kubectl label namespace test-ns nginx-injector=enabled                                
   kubectl -n test-ns run alpine --image=alpine                                          

