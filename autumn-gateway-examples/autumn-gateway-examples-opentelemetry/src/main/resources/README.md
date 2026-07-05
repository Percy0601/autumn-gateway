## Build Problem

根据你之前的情况，EL1008E的根本原因是 ConsulServiceInstance的 getter 没进反射 hints。我已经给了你修正后的 GatewayConsulNativeHints（只包含 ConsulServiceInstance+ ConsulDiscoveryClient），删掉了 ConsulService那段。



### 1. 清 maven 缓存里 cloud 5.0.2（防假 jar）
```
rm -rf ~/.m2/repository/org/springframework/cloud/spring-cloud-context/5.0.2
rm -rf ~/.m2/repository/org/springframework/cloud/spring-cloud-commons/5.0.2
rm -rf ~/.m2/repository/org/springframework/cloud/spring-cloud-consul-discovery/5.0.2
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


---

```

IllegalArgumentException:
  GenericApplicationContext must be instance of AnnotationConfigRegistry
  Suppressed checkpoint: WeightCalculatorWebFilter@391bba7a
  Original: Assert.instanceCheckFailed
```

### 这是 SCG 5.0.2 + Boot 4.1 + Native 的已知坑

SCG 5.x 这条线在 Cloud 2025.0.x / 2025.1.x 的 Native 支持还在补洞阶段，NamedContextFactory这个 assert 在 SCG 5.0.3 / Cloud 2025.1.3​ 会修（把 assert 放宽成 GenericApplicationContext也能过，或 AOT 层保留 AnnotationConfigApplicationContext的 AnnotationConfigRegistry身份）。
你当前版本链：
- Boot 4.1.0
- Cloud 2025.1.2
- SCG 5.0.2 ← 坑在这




