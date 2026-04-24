# 上传文件保存失败修复

## 现象

点击"解析文档"上传 PDF 时后端报错：

```
FileNotFoundException: C:\Users\...\Tomcat\localhost\ROOT\.\uploads\<uuid>.pdf
```

路径里出现 `...\work\Tomcat\localhost\ROOT\.\uploads\...`，不是项目里配置的 `./uploads`。

## 根因

`MultipartFile.transferTo(File)` 对**相对路径**的解析规则是：以 Servlet 容器临时工作目录（Tomcat `work/...`）为基准，而不是 JVM 工作目录。

- `Files.createDirectories(Paths.get("./uploads"))` 创建的是 `backend/uploads/`（相对 JVM CWD）——目录真实存在。
- 但随后 `file.transferTo(Paths.get("./uploads/xxx.pdf").toFile())` 的相对路径被 Tomcat 解析到 work 目录，该目录下没有 `uploads` → 写文件失败。

## 修复

`@backend/src/main/java/com/smartedu/controller/UploadController.java:92-93`：在构造 `uploadDir` 时显式转绝对路径并规范化。

```java
Path uploadDir = Paths.get(uploadPath).toAbsolutePath().normalize();
```

之后 `uploadDir.resolve(uniqueFilename)` 得到的 `filePath` 也是绝对路径，`transferTo` 不再触发容器 work 目录解析逻辑。数据库 `parse_task.file_path` 也写入绝对路径，后续异步解析按绝对路径读取不受影响。

## 影响范围

- 仅 `UploadController.uploadFile` 一处，1 行改动 + 1 行注释。
- 与 `storage.upload-path` 配置兼容：相对路径（如 `./uploads`）继续按 JVM 工作目录解析；绝对路径直接保留。
- 不改接口、不改 DB、不动其他调用方。

## 验证

1. 启动后端，`POST /api/upload/file` 上传 PDF。
2. 期望：返回 `文件上传成功，正在解析中`；`backend/uploads/<uuid>.pdf` 实际存在。
3. 检查 `parse_task.file_path` 为绝对路径。

## 备注

日志中乱码 `绯荤粺鎵句笉鍒版寚瀹氱殑璺緞` 是 Windows 控制台以 GBK 显示 UTF-8 字节（"系统找不到指定的路径"）的结果，与本 bug 无因果关系，本次未处理。
