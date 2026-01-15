mago 3DTerrainer 
===

## 개요
mago 3DTerrainer는 자바로 개발된 오픈소스 기반의 quantized-mesh terrain 생성기입니다.   
Cesium Terrain Tiles의 기본 Terrain 데이터인 quantized-mesh와 호환됩니다.   
OCG의 표준 포맷인 GeoTIFF 파일을 공간정보 레스터 데이터를 손쉽게 quantized-mesh 데이터로 변환할 수 있습니다.   
참고 : https://github.com/CesiumGS/quantized-mesh

![Static Badge](https://img.shields.io/badge/Gaia3D%2C%20Inc-blue?style=flat-square)
![Static Badge](https://img.shields.io/badge/QuantizedMesh-green?style=flat-square&logo=Cesium)
![Static Badge](https://img.shields.io/badge/Jdk17-red?style=flat-square&logo=openjdk)
![Static Badge](https://img.shields.io/badge/Gradle-darkorange?style=flat-square&logo=gradle)
![Static Badge](https://img.shields.io/badge/Docker%20Image-blue?style=flat-square&logo=docker)

![images2-mini](https://github.com/user-attachments/assets/be2b046a-3a79-4415-a16f-a0607976c62c)

## 주요 기능
- 편리한 변환: GeoTIFF 파일을 복잡한 커맨드 없이 변환할 수 있습니다.
- 높은 정확도: 높은 정확도의 quantized-mesh 데이터를 생성합니다.
- 다수의 데이터 변환: 다수의 GeoTIFF 데이터를 한 번에 변환할 수 있습니다.
- 다중 천체 지원: 지구와 달에 대한 지형 생성을 적절한 좌표 참조 시스템으로 지원합니다.
- 상세옵션 조절: 최소/최대 타일 깊이, 타일 레스터 최대 크기, 타일 모자이크 크기, 타일생성 강도, 보간방법 등 다양한 상세옵션을 제공합니다.

## 사용법
Release된 jar파일을 실행하거나, mago-3d-terrainer를 직접 빌드하여 jar를 생성할 수 있습니다.
- mago-3d-terrainer-1.x.x.jar

```
gradlew jar
```

***빌드 시 사용된 java 버전은 openjdk 17 입니다.***

#### 도움말 명령어 예시
```
java -jar mago-3d-terrainer-x.x.x.jar -help
```
출력 결과물:
```
┳┳┓┏┓┏┓┏┓  ┏┓┳┓  ┏┳┓┏┓┳┓┳┓┏┓┳┳┓┏┓┳┓
┃┃┃┣┫┃┓┃┃   ┫┃┃   ┃ ┣ ┣┫┣┫┣┫┃┃┃┣ ┣┫
┛ ┗┛┗┗┛┗┛  ┗┛┻┛   ┻ ┗┛┛┗┛┗┛┗┻┛┗┗┛┛┗
3d-terrainer(dev-version) by Gaia3D, Inc.
----------------------------------------
usage: mago 3DTerrainer help
 -c,--continue                   Continue from last terrain generation. This option can be used when terrain creation is interrupted or fails.
 -cn,--calculateNormals          Add terrain octVertexNormals for lighting effect
 -d,--debug                      Debug Mode, print more detail log
 -h,--help                       Print this message
 -i,--input <arg>                Input folder path
 -is,--intensity <arg>           Mesh refinement strength. (default : 4.0)
 -it,--interpolationType <arg>   Interpolation type (nearest, bilinear) (default : bilinear)
 -j,--json                       Generate only layer.json from terrain data
 -l,--log <arg>                  Log file path
 -lt,--leaveTemp                 Leave temporary files for debugging
 -max,--maxDepth <arg>           Maximum tile depth (range : 0 ~ 22) (default : 14)
 -min,--minDepth <arg>           Minimum tile depth (range : 0 ~ 22) (default : 0)
 -mr,--rasterMaxSize <arg>       Maximum raster size for split function. (default : 8192)
 -ms,--mosaicSize <arg>          Tiling mosaic buffer size per tile. (default : 16)
 -nv,--nodataValue <arg>         No data value for terrain data (default : -9999)
 -o,--output <arg>               Output folder path
 -pt,--priorityType <arg>        Priority type () (default : distance)
```
필수 인자 값으로 작성한 간단한 GeoTIFF 변환코드 입니다.
```
java -jar mago-3d-terrainer-x.x.x.jar -input C:\data\geotiff-sample -output C:\data\geotiff-terrain-output -maxDepth 14
```
또는
```
java -jar mago-3d-terrainer-x.x.x.jar -input C:\data\geotiff-sample -output C:\data\geotiff-terrain-output -maxDepth 14
```

## 도커 버전 사용법
mago 3DTerrainer는 도커 이미지로도 사용이 가능합니다.

사용 예시:
```
docker pull gaia3d/mago-3d-terrainer
```
```
docker run --rm -v "/workspace:/workspace" gaia3d/mago-3d-terrainer -input /workspace/geotiff-sample -output /workspace/geotiff-terrain-output -maxDepth 14
```

## 천체 지원
mago 3DTerrainer는 여러 천체에 대한 지형 생성을 지원합니다:
- **지구** (기본값): WGS84 좌표계(EPSG:4326)를 사용하는 타원체 모델
- **달**: IAU 2015 좌표계(IAU:30100)를 사용하는 구형 모델 (반지름: 1,737.4 km)

`-body` 또는 `-b` 옵션을 사용하여 대상 천체를 지정합니다:

```bash
# 지구 지형 생성 (기본값)
java -jar mago-3d-terrainer.jar -input /earth_data -output /output/earth

# 달 지형 생성
java -jar mago-3d-terrainer.jar -input /moon_data -output /output/moon -body moon

# 사용자 정의 깊이 및 강도를 가진 달 지형
java -jar mago-3d-terrainer.jar -input /moon_data -output /output/moon -body moon -max 18 -is 5
```

달의 작은 반지름(지구의 약 27%)은 동일한 깊이 수준에서 더 작은 타일 크기를 생성합니다. 예를 들어, 깊이 14에서 타일은 달에서 약 3km, 지구에서 약 11km입니다.

## 문서
자세한 문서는 공식 문서를 참조하세요:
- JavaDocs : [gaia3d.github.io/mago-3d-terrainer](https://gaia3d.github.io/mago-3d-terrainer)
- 매뉴얼 : [github.com/Gaia3D/mago-3d-terrainer](https://github.com/Gaia3D/mago-3d-terrainer/blob/main/MANUAL.ko.md)

## 지원하는 자바 버전
JDK17, JDK21 등 LTS(Long-term support)버전 JDK를 지원합니다.

## 라이선스
- mago 3DTerrainer는 MPL2.0 라이선스를 따릅니다. (<https://www.mozilla.org/en-US/MPL/2.0/>)
- 만약 MPL2.0라이선스에 따라 여러분이 개작, 수정한 코드를 공개하고 싶지 않으면 상업 라이선스를 따르시면 됩니다. 이 경우에는 sales@gaia3d.com으로 연락 주시기 바랍니다.

## 라이브러리 의존성
- **JOML** (Java OpenGL Math Library): <https://github.com/JOML-CI/JOML>
- **geotools** (Geospatial data tools library): <https://github.com/geotools/geotools>
- **proj4j** (Converting coordinate reference systems): <https://github.com/locationtech/proj4j>