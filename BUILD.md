### 编译

> 使用`jdk 1.8`，不然会和`java.lang.Record`冲突。

```bash
mvn -U clean package assembly:assembly -Dmaven.test.skip=true
```

### 添加插件

1. 修改 pom.xml
2. 修改 package.xml