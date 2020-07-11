build: build-image
run: run-image

build-image:
	./gradlew clean build --info
	$(call docker_build)

run-image:
	docker run rafael/twitter-stream-controller

define docker_build
	docker build --file=dist/docker/Dockerfile \
	--rm \
	-t rafael/twitter-stream-controller:latest .

endef