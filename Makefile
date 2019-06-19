clean:
	@sbt clean
	@rm -rf ./target/

package: clean
	@sbt package

publish:
	@sbt docker:publishLocal

build: package publish
	@docker tag lamport-clocks:1.0 gcr.io/lamport-clocks-0619/lamport-clocks
	@docker push gcr.io/lamport-clocks-0619/lamport-clocks

build-local: package publish

deploy-standalone:
	@kubectl apply -f ./kubernetes/akka-cluster.yml

deploy-standalone-local:
	@kubectl apply -f ./kubernetes/akka-cluster-local.yml

kill:
	@kubectl delete deployment lamport-clocks
	@kubectl delete service lamport-clocks

local:
	@./kubernetes/setup-minikube-for-linux.sh

deploy: build deploy-standalone
redeploy: kill build deploy-standalone

deploy-local: build-local deploy-standalone-local
redeploy-local: kill build-local deploy-standalone-local
