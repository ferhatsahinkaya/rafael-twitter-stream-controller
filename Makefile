build: build-image

build-image:
	./gradlew clean build --info
	$(call docker_build)

define docker_build
	docker build --file=dist/docker/Dockerfile \
	--rm \
	-t rafael/twitter-stream-controller:latest .

endef