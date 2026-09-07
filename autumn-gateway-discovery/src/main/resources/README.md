## Build Problem


### 1. 清 maven 缓存里 cloud 5.0.2（防假 jar）
```
rm -rf ~/.m2/repository/org/springframework/cloud/spring-cloud-context/5.0.2
rm -rf ~/.m2/repository/org/springframework/cloud/spring-cloud-commons/5.0.2

```

### 2. 清 AOT + native 产物
```
mvn -Pnative clean
```


### 3. 强刷重拉 + 重编
```
mvn -U -Pnative native:compile
```

### 4. 如果还炸，JVM 模式先验证 AOT 产物
```
mvn -Pnative spring-boot:process-aot
find target -name "*.imports" -path "*spring*" | xargs grep -l "RefreshBootstrap"

```
- 有输出 → 还有某个依赖的 jar 内自带老 imports，定位它
- 没输出 → AOT 层干净，问题在 native-image 运行期别的路径（agent / reflect-config）
