# -*- coding: utf-8 -*-
"""生成全世界国家词库：拼音 + 中文国名，一行一条。"""
import os

try:
    from pypinyin import lazy_pinyin
    def to_pinyin(s):
        return "".join(lazy_pinyin(s, style=0))
except ImportError:
    # 无 pypinyin 时使用内置表（常用国名拼音）
    _PINYIN = {
        "阿富汗": "afuhan", "阿尔巴尼亚": "aerbaniya", "阿尔及利亚": "aerjiliya",
        "安道尔": "andaoer", "安哥拉": "angela", "安提瓜和巴布达": "antiguahebabuda",
        "阿根廷": "agenting", "亚美尼亚": "yameiniya", "澳大利亚": "aodaliya",
        "奥地利": "aodili", "阿塞拜疆": "asaibaijiang", "巴哈马": "bahama",
        "巴林": "balin", "孟加拉国": "mengjialaguo", "巴巴多斯": "babaduosi",
        "白俄罗斯": "baieluosi", "比利时": "bilishi", "伯利兹": "bolizi",
        "贝宁": "beining", "不丹": "budan", "玻利维亚": "boliweiya",
        "波黑": "bohei", "博茨瓦纳": "bociwana", "巴西": "baxi",
        "文莱": "wenlai", "保加利亚": "baojialiya", "布基纳法索": "bujinafasuo",
        "布隆迪": "bulongdi", "佛得角": "fodejiao", "柬埔寨": "jianpuzhai",
        "喀麦隆": "kamailong", "加拿大": "jianada", "中非": "zhongfei",
        "乍得": "zhade", "智利": "zhili", "中国": "zhongguo",
        "哥伦比亚": "gelunbiya", "科摩罗": "kemoluo", "刚果共和国": "gangguogongheguo",
        "哥斯达黎加": "gesidalijia", "科特迪瓦": "ketediwa", "克罗地亚": "keluodiya",
        "古巴": "guba", "塞浦路斯": "saipulusi", "捷克": "jieke",
        "朝鲜": "chaoxian", "刚果民主共和国": "gangguominzhugongheguo",
        "丹麦": "danmai", "吉布提": "jibuti", "多米尼克": "duominike",
        "多米尼加": "duominijia", "厄瓜多尔": "eguaduoer", "埃及": "aiji",
        "萨尔瓦多": "saerwaduo", "赤道几内亚": "chidaojineiya", "厄立特里亚": "eliteliya",
        "爱沙尼亚": "aishaniya", "斯威士兰": "siweishilan", "埃塞俄比亚": "aisaiebiya",
        "斐济": "feiji", "芬兰": "fenlan", "法国": "faguo",
        "加蓬": "jiapeng", "冈比亚": "gangbiya", "格鲁吉亚": "gelujiya",
        "德国": "deguo", "加纳": "jiana", "希腊": "xila",
        "格林纳达": "gelingnada", "危地马拉": "weidimala", "几内亚": "jineiya",
        "几内亚比绍": "jineiyabishao", "圭亚那": "guiyana", "海地": "haidi",
        "洪都拉斯": "hongdulasi", "匈牙利": "xiongyali", "冰岛": "bingdao",
        "印度": "yindu", "印度尼西亚": "yindunixiya", "伊朗": "yilang",
        "伊拉克": "yilake", "爱尔兰": "aierlan", "以色列": "yiselie",
        "意大利": "yidali", "牙买加": "yamaijia", "日本": "riben",
        "约旦": "yuedan", "哈萨克斯坦": "hasakesitan", "肯尼亚": "kenniya",
        "基里巴斯": "jilibasi", "科威特": "keweite", "吉尔吉斯斯坦": "jierjisisitan",
        "老挝": "laowo", "拉脱维亚": "latuoweiya", "黎巴嫩": "libanen",
        "莱索托": "laisuotuo", "利比里亚": "libiliya", "利比亚": "libiya",
        "列支敦士登": "liezhidunshideng", "立陶宛": "litaowan", "卢森堡": "lusenbao",
        "马达加斯加": "madajiasijia", "马拉维": "malawei", "马来西亚": "malaixiya",
        "马尔代夫": "maerdaifu", "马里": "mali", "马耳他": "maerta",
        "马绍尔群岛": "mashaoerqundao", "毛里塔尼亚": "maolitaniya", "毛里求斯": "maoliqiusi",
        "墨西哥": "moxige", "密克罗尼西亚": "mikeluonixiya", "摩纳哥": "monage",
        "蒙古": "menggu", "黑山": "heishan", "摩洛哥": "moluoge",
        "莫桑比克": "mosangbike", "缅甸": "miandian", "纳米比亚": "namibiya",
        "瑙鲁": "naolu", "尼泊尔": "niboer", "荷兰": "helan",
        "新西兰": "xinxilan", "尼加拉瓜": "nijialagua", "尼日尔": "nirier",
        "尼日利亚": "niriliya", "北马其顿": "beimaqidun", "挪威": "nuowei",
        "阿曼": "aman", "巴基斯坦": "bajisitan", "帕劳": "palao",
        "巴拿马": "banama", "巴布亚新几内亚": "babuyaxinjineiya", "巴拉圭": "balagui",
        "秘鲁": "bilu", "菲律宾": "feilvbin", "波兰": "bolan",
        "葡萄牙": "putaoya", "卡塔尔": "kataer", "韩国": "hanguo",
        "摩尔多瓦": "moerduowa", "罗马尼亚": "luomaniya", "俄罗斯": "eluosi",
        "卢旺达": "luwanda", "圣基茨和尼维斯": "shengjiciheniweisi", "圣卢西亚": "shengluxiya",
        "圣文森特和格林纳丁斯": "shengwensentehegelingnadingsi", "萨摩亚": "samoya",
        "圣马力诺": "shengmalinuo", "圣多美和普林西比": "shengduomeihepulinxibi",
        "沙特阿拉伯": "shatealabo", "塞内加尔": "saineijiaer", "塞尔维亚": "saierweiya",
        "塞舌尔": "saisheer", "塞拉利昂": "sailaliang", "新加坡": "xinjiapo",
        "斯洛伐克": "siluofake", "斯洛文尼亚": "siluowenniya", "所罗门群岛": "suoluomenqundao",
        "索马里": "suomali", "南非": "nanfei", "南苏丹": "nansudan",
        "西班牙": "xibanya", "斯里兰卡": "sililanka", "苏丹": "sudan",
        "苏里南": "sulinan", "瑞典": "ruidian", "瑞士": "ruishi",
        "叙利亚": "xuliya", "塔吉克斯坦": "tajikesitan", "泰国": "taiguo",
        "东帝汶": "dongdiwen", "多哥": "duoge", "汤加": "tangjia",
        "特立尼达和多巴哥": "telinidaheduobage", "突尼斯": "tunisi",
        "土耳其": "tuerqi", "土库曼斯坦": "tukumansitan", "图瓦卢": "tuwalu",
        "乌干达": "wuganda", "乌克兰": "wukelan", "阿联酋": "alianqiu",
        "英国": "yingguo", "坦桑尼亚": "tansangniya", "美国": "meiguo",
        "乌拉圭": "wulagui", "乌兹别克斯坦": "wuzibiekesitan", "瓦努阿图": "wanuatu",
        "委内瑞拉": "weineiruila", "越南": "yuenan", "也门": "yemen",
        "赞比亚": "zanbiya", "津巴布韦": "jinbabuwei", "梵蒂冈": "fandigang",
        "巴勒斯坦": "balesitan",
    }
    def to_pinyin(s):
        return _PINYIN.get(s, s)

ASSETS = os.path.dirname(os.path.abspath(__file__))
OUT_FILE = os.path.join(ASSETS, "pinyin_words_countries.txt")

# 联合国 193 国 + 梵蒂冈、巴勒斯坦（中文常用名，与外交部/新华社一致）
COUNTRIES_ZH = [
    "阿富汗", "阿尔巴尼亚", "阿尔及利亚", "安道尔", "安哥拉", "安提瓜和巴布达",
    "阿根廷", "亚美尼亚", "澳大利亚", "奥地利", "阿塞拜疆", "巴哈马", "巴林",
    "孟加拉国", "巴巴多斯", "白俄罗斯", "比利时", "伯利兹", "贝宁", "不丹",
    "玻利维亚", "波黑", "博茨瓦纳", "巴西", "文莱", "保加利亚", "布基纳法索",
    "布隆迪", "佛得角", "柬埔寨", "喀麦隆", "加拿大", "中非", "乍得", "智利",
    "中国", "哥伦比亚", "科摩罗", "刚果共和国", "哥斯达黎加", "科特迪瓦",
    "克罗地亚", "古巴", "塞浦路斯", "捷克", "朝鲜", "刚果民主共和国", "丹麦",
    "吉布提", "多米尼克", "多米尼加", "厄瓜多尔", "埃及", "萨尔瓦多",
    "赤道几内亚", "厄立特里亚", "爱沙尼亚", "斯威士兰", "埃塞俄比亚", "斐济",
    "芬兰", "法国", "加蓬", "冈比亚", "格鲁吉亚", "德国", "加纳", "希腊",
    "格林纳达", "危地马拉", "几内亚", "几内亚比绍", "圭亚那", "海地", "洪都拉斯",
    "匈牙利", "冰岛", "印度", "印度尼西亚", "伊朗", "伊拉克", "爱尔兰", "以色列",
    "意大利", "牙买加", "日本", "约旦", "哈萨克斯坦", "肯尼亚", "基里巴斯",
    "科威特", "吉尔吉斯斯坦", "老挝", "拉脱维亚", "黎巴嫩", "莱索托", "利比里亚",
    "利比亚", "列支敦士登", "立陶宛", "卢森堡", "马达加斯加", "马拉维", "马来西亚",
    "马尔代夫", "马里", "马耳他", "马绍尔群岛", "毛里塔尼亚", "毛里求斯", "墨西哥",
    "密克罗尼西亚", "摩纳哥", "蒙古", "黑山", "摩洛哥", "莫桑比克", "缅甸",
    "纳米比亚", "瑙鲁", "尼泊尔", "荷兰", "新西兰", "尼加拉瓜", "尼日尔", "尼日利亚",
    "北马其顿", "挪威", "阿曼", "巴基斯坦", "帕劳", "巴拿马", "巴布亚新几内亚",
    "巴拉圭", "秘鲁", "菲律宾", "波兰", "葡萄牙", "卡塔尔", "韩国", "摩尔多瓦",
    "罗马尼亚", "俄罗斯", "卢旺达", "圣基茨和尼维斯", "圣卢西亚", "圣文森特和格林纳丁斯",
    "萨摩亚", "圣马力诺", "圣多美和普林西比", "沙特阿拉伯", "塞内加尔", "塞尔维亚",
    "塞舌尔", "塞拉利昂", "新加坡", "斯洛伐克", "斯洛文尼亚", "所罗门群岛", "索马里",
    "南非", "南苏丹", "西班牙", "斯里兰卡", "苏丹", "苏里南", "瑞典", "瑞士",
    "叙利亚", "塔吉克斯坦", "泰国", "东帝汶", "多哥", "汤加", "特立尼达和多巴哥",
    "突尼斯", "土耳其", "土库曼斯坦", "图瓦卢", "乌干达", "乌克兰", "阿联酋",
    "英国", "坦桑尼亚", "美国", "乌拉圭", "乌兹别克斯坦", "瓦努阿图", "委内瑞拉",
    "越南", "也门", "赞比亚", "津巴布韦", "梵蒂冈", "巴勒斯坦",
]

def main():
    lines = []
    for name in COUNTRIES_ZH:
        py = to_pinyin(name)
        lines.append("%s %s" % (py, name))
    with open(OUT_FILE, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines))
        f.write("\n")
    print("已写入 %d 条: %s" % (len(lines), OUT_FILE))

if __name__ == "__main__":
    main()
