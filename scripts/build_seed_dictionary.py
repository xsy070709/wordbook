#!/usr/bin/env python3
"""Build a tiny development dictionary so a fresh checkout can launch offline."""

from pathlib import Path
import sqlite3


ENTRIES = [
    ("abandon", "əˈbændən", "v. 放弃；抛弃；遗弃\nn. 放任；放纵"),
    ("ability", "əˈbɪləti", "n. 能力；才能"),
    ("accept", "əkˈsept", "v. 接受；同意；承认"),
    ("achieve", "əˈtʃiːv", "v. 实现；达到；取得"),
    ("allude", "əˈluːd", "v. 暗指；影射；间接提及"),
    ("answer", "ˈɑːnsə", "n. 回答；答案\nv. 回答；回应"),
    ("book", "bʊk", "n. 书；本子\nv. 预订"),
    ("build", "bɪld", "v. 建造；建立\nn. 体格；构造"),
    ("change", "tʃeɪndʒ", "v. 改变；更换\nn. 变化；零钱"),
    ("coerce", "kəʊˈɜːs", "v. 强迫；胁迫"),
    ("common", "ˈkɒmən", "adj. 常见的；共同的；普通的"),
    ("contemplate", "ˈkɒntəmpleɪt", "v. 沉思；考虑；注视"),
    ("create", "kriˈeɪt", "v. 创造；创建；造成"),
    ("dictionary", "ˈdɪkʃənri", "n. 词典；字典"),
    ("example", "ɪɡˈzɑːmpəl", "n. 例子；榜样；范例"),
    ("find", "faɪnd", "v. 找到；发现；认为"),
    ("fortify", "ˈfɔːtɪfaɪ", "v. 加强；增强；筑防御工事"),
    ("friend", "frend", "n. 朋友；友人"),
    ("hello", "həˈləʊ", "int. 你好；喂\nn. 招呼声"),
    ("help", "help", "v. 帮助；援助\nn. 帮助；助手"),
    ("inevitably", "ɪnˈevɪtəbli", "adv. 不可避免地；必然地"),
    ("language", "ˈlæŋɡwɪdʒ", "n. 语言；表达方式"),
    ("learn", "lɜːn", "v. 学习；得知；认识到"),
    ("meaning", "ˈmiːnɪŋ", "n. 意思；含义；意义"),
    ("memory", "ˈmeməri", "n. 记忆；回忆；存储器"),
    ("notebook", "ˈnəʊtbʊk", "n. 笔记本；手册"),
    ("personal", "ˈpɜːsənəl", "adj. 个人的；私人的；亲自的"),
    ("query", "ˈkwɪəri", "n. 查询；疑问\nv. 询问；质疑"),
    ("read", "riːd", "v. 阅读；读懂；显示"),
    ("remember", "rɪˈmembə", "v. 记得；想起；牢记"),
    ("review", "rɪˈvjuː", "n. 复习；审查；评论\nv. 复习；审查"),
    ("search", "sɜːtʃ", "v. 搜索；搜查\nn. 搜索；检索"),
    ("select", "sɪˈlekt", "v. 选择；挑选\nadj. 精选的"),
    ("simple", "ˈsɪmpəl", "adj. 简单的；朴素的；单纯的"),
    ("study", "ˈstʌdi", "v. 学习；研究\nn. 学习；研究"),
    ("translate", "trænzˈleɪt", "v. 翻译；转化；解释"),
    ("vocabulary", "vəˈkæbjələri", "n. 词汇；词汇量；词表"),
    ("word", "wɜːd", "n. 单词；话语；消息"),
]


def main() -> None:
    output = Path("app/src/main/assets/database/dictionary.db")
    output.parent.mkdir(parents=True, exist_ok=True)
    if output.exists():
        output.unlink()
    connection = sqlite3.connect(output)
    connection.execute(
        "CREATE TABLE words (word TEXT PRIMARY KEY COLLATE NOCASE, "
        "phonetic TEXT NOT NULL DEFAULT '', translation TEXT NOT NULL) WITHOUT ROWID"
    )
    connection.executemany("INSERT INTO words VALUES (?, ?, ?)", ENTRIES)
    connection.commit()
    connection.close()
    print(f"Built development seed dictionary with {len(ENTRIES)} entries: {output}")


if __name__ == "__main__":
    main()
