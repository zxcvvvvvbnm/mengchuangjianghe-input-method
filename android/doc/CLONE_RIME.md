# RIME 已从本工程移除（历史参考）

本工程 **已不再使用 RIME**。26 键拼音由 **PinyinDict（内置词库）+ Pinyin4j** 实现，无 native、无部署。

以下为原 RIME 集成说明，仅作参考：

- 曾用：librime.so + assets/rime_shared + JNI RimeBridge。
- 现用：纯 Java/Kotlin，PinyinDict 词表 + Pinyin4j 依赖（可后续用于拼音规范化或汉字→拼音）。
