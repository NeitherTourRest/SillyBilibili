database(SQLite)
---
data/local/dao(sql)
---
data/repository(Impl, 调用DAO的sql语句，返回entity，并把entity map成domain/model中的模型)
---
domain/repository(前端交互的接口，通过这个调用其实现，得到domain模型)