import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.Reader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

import org.wltea.analyzer.core.IKSegmenter;
import org.wltea.analyzer.core.Lexeme;

/**
 * 文本摘要提取文中重要的关键句子，使用top-n关键词在句子中的比例关系
 * 返回过滤句子方法为：1.均值标准差，2.top-n句子，3.最大边缘相关top-n句子
 *
 * @author yan.shi
 *@date： 日期：2017-4-20 时间：上午9:45:21
 */
public class NewsSummary {
    int N=50;//保留关键词数量
    int CLUSTER_THRESHOLD=5;//关键词间的距离阀值
    int TOP_SENTENCES=10;//前top-n句子
    double λ=0.4;//最大边缘相关阀值
    final Set<String> styleSet=new HashSet<String>();//句子得分使用方法
    Set<String> stopWords=new HashSet<String>();//过滤停词
    Map<Integer,List<String>> sentSegmentWords=null;//句子编号及分词列表

    /**
     * 加载停词
     * @param path
     */
    private void loadStopWords(String path){
        BufferedReader br=null;
        try{
            br=new BufferedReader(new InputStreamReader(new FileInputStream(path),"utf-8"));
            String line=null;
            while((line=br.readLine())!=null){
                stopWords.add(line);
            }
            br.close();
        }catch(IOException e){
            e.printStackTrace();
        }
    }

    public NewsSummary(){
        this.loadStopWords("F:\\需要要改进\\stopwords.txt");
        styleSet.add("meanstd");
        styleSet.add("default");
        styleSet.add("MMR");
    }

    /**
     * 每个句子得分  (keywordsLen*keywordsLen/totalWordsLen)
     * @param sentences 分句
     * @param topnWords keywords top-n关键词
     * @return
     */
    private Map<Integer,Double> scoreSentences(List<String> sentences,List<String> topnWords){
        //System.out.println("scoreSentences in...");
        Map<Integer, Double> scoresMap=new LinkedHashMap<Integer,Double>();//句子编号，得分
        sentSegmentWords=new HashMap<Integer,List<String>>();
        int sentence_idx=-1;//句子编号
        for(String sentence:sentences){
            sentence_idx+=1;
            List<String> words=this.IKSegment(sentence);//对每个句子分词
            sentSegmentWords.put(sentence_idx, words);
            List<Integer> word_idx=new ArrayList<Integer>();//每个关词键在本句子中的位置
            for(String word:topnWords){
                if(words.contains(word)){
                    word_idx.add(words.indexOf(word));
                }else
                    continue;
            }
            if(word_idx.size()==0)
                continue;
            Collections.sort(word_idx);
            //对于两个连续的单词，利用单词位置索引，通过距离阀值计算一个族
            List<List<Integer>> clusters=new ArrayList<List<Integer>>();//根据本句中的关键词的距离存放多个词族
            List<Integer> cluster=new ArrayList<Integer>();
            cluster.add(word_idx.get(0));
            int i=1;
            while(i<word_idx.size()){
                if((word_idx.get(i)-word_idx.get(i-1))<this.CLUSTER_THRESHOLD)
                    cluster.add(word_idx.get(i));
                else{
                    clusters.add(cluster);
                    cluster=new ArrayList<Integer>();
                    cluster.add(word_idx.get(i));
                }
                i+=1;
            }
            clusters.add(cluster);
            //对每个词族打分，选择最高得分作为本句的得分
            double max_cluster_score=0.0;
            for(List<Integer> clu:clusters){
                int keywordsLen=clu.size();//关键词个数
                int totalWordsLen=clu.get(keywordsLen-1)-clu.get(0)+1;//总的词数
                double score=1.0*keywordsLen*keywordsLen/totalWordsLen;
                if(score>max_cluster_score)
                    max_cluster_score=score;
            }
            scoresMap.put(sentence_idx,max_cluster_score);
        }
        //System.out.println("scoreSentences out...");
        return scoresMap;
    }

    /**
     * 这里使用IK进行分词
     * @param text
     * @return
     */
    private List<String> IKSegment(String text){
        List<String> wordList=new ArrayList<String>();
        Reader reader=new StringReader(text);
        IKSegmenter ik=new IKSegmenter(reader,true);
        Lexeme lex=null;
        try {
            while((lex=ik.next())!=null){
                String word=lex.getLexemeText();
                if(word.equals("nbsp") || this.stopWords.contains(word))
                    continue;
                if(word.length()>1 && word!="\t")
                    wordList.add(word);
            }
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return wordList;
    }

    /**
     * 分句
     * @param text
     * @return
     */
    private List<String> sentTokenizer(String text){
        //System.out.println("sentTokenizer in...");
        List<String> sentences=new ArrayList<String>();
        String regEx="[!?。！？.;]";
        Pattern p=Pattern.compile(regEx);
        Matcher m=p.matcher(text);
        String[] sent=p.split(text);
        int sentLen=sent.length;
        if(sentLen>0){
            int count=0;
            while(count<sentLen){
                if(m.find()){
                    sent[count]+=m.group();
                }
                count++;
            }
        }
        for(String sentence:sent){
            sentence=sentence.replaceAll("(&rdquo;|&ldquo;|&mdash;|&lsquo;|&rsquo;|&middot;|&quot;|&darr;|&bull;)", "");
            sentences.add(sentence.trim());
        }
        //System.out.println("sentTokenizer out...");
        return sentences;
    }

    /**
     * 计算文本摘要
     * @param text
     * @param style(meanstd,default,MMR)
     * @return
     */
    public String summarize(String text,String style){
        //System.out.println("summarize in...");
        try {
            if(!styleSet.contains(style) || text.trim().equals(""))
                throw new IllegalArgumentException("方法 summarize(String text,String style)中text不能为空，style必须是meanstd、default或者MMR");
        } catch (Exception e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.exit(1);
        }

        List<String> sentencesList=this.sentTokenizer(text);
        List<String> words=this.IKSegment(text);
        Map<String,Integer> wordsMap=new HashMap<String,Integer>();
        for(String word:words){
            Integer val=wordsMap.get(word);
            wordsMap.put(word,val==null?1:val+1);
        }
        //使用优先队列自动排序
        Queue<Map.Entry<String, Integer>> wordsQueue=new PriorityQueue<Map.Entry<String,Integer>>(wordsMap.size(),new Comparator<Map.Entry<String,Integer>>(){
            @Override
            public int compare(Entry<String, Integer> o1,
                               Entry<String, Integer> o2) {
                // TODO Auto-generated method stub
                return o2.getValue()-o1.getValue();
            }
        });
        wordsQueue.addAll(wordsMap.entrySet());
        if(N>wordsMap.size()) N=wordsQueue.size();
        List<String> wordsList=new ArrayList<String>(N);//top-n关键词
        for(int i=0;i<N;i++){
            Entry<String,Integer> entry=wordsQueue.poll();
            wordsList.add(entry.getKey());
        }

        Map<Integer,Double> scoresLinkedMap=scoreSentences(sentencesList,wordsList);//返回的得分,从第一句开始,句子编号的自然顺序
        List<Map.Entry<Integer, Double>> sortedSentList=new ArrayList<Map.Entry<Integer,Double>>(scoresLinkedMap.entrySet());//按得分从高到底排序好的句子，句子编号与得分
        //System.setProperty("java.util.Arrays.useLegacyMergeSort", "true");
        Collections.sort(sortedSentList, new Comparator<Map.Entry<Integer, Double>>(){
            @Override
            public int compare(Entry<Integer, Double> o1,Entry<Integer, Double> o2) {
                // TODO Auto-generated method stub
                return o2.getValue() == o1.getValue() ? 0 :
                        (o2.getValue() > o1.getValue() ? 1 : -1);
            }

        });

        Map<Integer,String> keySentence=null;

        //approach1,利用均值和标准差过滤非重要句子
        if(style.equals("meanstd")){
            keySentence=new LinkedHashMap<Integer,String>();
            double sentenceMean=0.0;//句子得分均值
            for(double value:scoresLinkedMap.values()){
                sentenceMean+=value;
            }
            sentenceMean/=scoresLinkedMap.size();
            double sentenceStd=0.0;//句子得分标准差
            for(Double score:scoresLinkedMap.values()){
                sentenceStd+=Math.pow((score-sentenceMean), 2);
            }
            sentenceStd=Math.sqrt(sentenceStd/scoresLinkedMap.size());
            for(Map.Entry<Integer, Double> entry:scoresLinkedMap.entrySet()){
                if(entry.getValue()>(sentenceMean+0.5*sentenceStd))//过滤低分句子
                    keySentence.put(entry.getKey(), sentencesList.get(entry.getKey()));
            }
        }

        //approach2,默认返回排序得分top-n句子
        if(style.equals("default")){
            keySentence=new TreeMap<Integer,String>();
            int count=0;
            for(Map.Entry<Integer, Double> entry:sortedSentList){
                count++;
                keySentence.put(entry.getKey(), sentencesList.get(entry.getKey()));
                if(count==this.TOP_SENTENCES)
                    break;
            }
        }

        //approach3,利用最大边缘相关，返回前top-n句子
        if(style.equals("MMR")){
            if(sentencesList.size()==2){
                return sentencesList.get(0)+sentencesList.get(1);
            }else if(sentencesList.size()==1)
                return sentencesList.get(0);
            keySentence=new TreeMap<Integer,String>();
            int count=0;
            Map<Integer,Double> MMR_SentScore=MMR(sortedSentList);
            for(Map.Entry<Integer, Double> entry:MMR_SentScore.entrySet()){
                count++;
                int sentIndex=entry.getKey();
                String sentence=sentencesList.get(sentIndex);
                keySentence.put(sentIndex, sentence);
                if(count==this.TOP_SENTENCES)
                    break;
            }
        }
        StringBuilder sb=new StringBuilder();
        for(int  index:keySentence.keySet())
            sb.append(keySentence.get(index));
        //System.out.println("summarize out...");
        return sb.toString();
    }

    /**
     * 最大边缘相关(Maximal Marginal Relevance)，根据λ调节准确性和多样性
     * max[λ*score(i) - (1-λ)*max[similarity(i,j)]]:score(i)句子的得分，similarity(i,j)句子i与j的相似度
     * User-tunable diversity through λ parameter
     * - High λ= Higher accuracy
     * - Low λ= Higher diversity
     * @param sortedSentList 排好序的句子，编号及得分
     * @return
     */
    private Map<Integer,Double> MMR(List<Map.Entry<Integer, Double>> sortedSentList){
        //System.out.println("MMR In...");
        double[][] simSentArray=sentJSimilarity();//所有句子的相似度
        Map<Integer,Double> sortedLinkedSent=new LinkedHashMap<Integer,Double>();
        for(Map.Entry<Integer, Double> entry:sortedSentList){
            sortedLinkedSent.put(entry.getKey(),entry.getValue());
        }
        Map<Integer,Double> MMR_SentScore=new LinkedHashMap<Integer,Double>();//最终的得分（句子编号与得分）
        Map.Entry<Integer, Double> Entry=sortedSentList.get(0);//第一步先将最高分的句子加入
        MMR_SentScore.put(Entry.getKey(), Entry.getValue());
        boolean flag=true;
        while(flag){
            int index=0;
            double maxScore=Double.NEGATIVE_INFINITY;//通过迭代计算获得最高分句子
            for(Map.Entry<Integer, Double> entry:sortedLinkedSent.entrySet()){
                if(MMR_SentScore.containsKey(entry.getKey())) continue;
                double simSentence=0.0;
                for(Map.Entry<Integer, Double> MMREntry:MMR_SentScore.entrySet()){//这个是获得最相似的那个句子的最大相似值
                    double simSen=0.0;
                    if(entry.getKey()>MMREntry.getKey())
                        simSen=simSentArray[MMREntry.getKey()][entry.getKey()];
                    else
                        simSen=simSentArray[entry.getKey()][MMREntry.getKey()];
                    if(simSen>simSentence){
                        simSentence=simSen;
                    }
                }
                simSentence=λ*entry.getValue()-(1-λ)*simSentence;
                if(simSentence>maxScore){
                    maxScore=simSentence;
                    index=entry.getKey();//句子编号
                }
            }
            MMR_SentScore.put(index, maxScore);
            if(MMR_SentScore.size()==sortedLinkedSent.size())
                flag=false;
        }
        //System.out.println("MMR out...");
        return MMR_SentScore;
    }

    /**
     * 每个句子的相似度，这里使用简单的jaccard方法，计算所有句子的两两相似度
     * @return
     */
    private double[][] sentJSimilarity(){
        //System.out.println("sentJSimilarity in...");
        int size=sentSegmentWords.size();
        double[][] simSent=new double[size][size];
        for(Map.Entry<Integer, List<String>> entry:sentSegmentWords.entrySet()){
            for(Map.Entry<Integer, List<String>> entry1:sentSegmentWords.entrySet()){
                if(entry.getKey()>=entry1.getKey()) continue;
                int commonWords=0;
                double sim=0.0;
                for(String entryStr:entry.getValue()){
                    if(entry1.getValue().contains(entryStr))
                        commonWords++;
                }
                sim=1.0*commonWords/(entry.getValue().size()+entry1.getValue().size()-commonWords);
                simSent[entry.getKey()][entry1.getKey()]=sim;
            }
        }
        //System.out.println("sentJSimilarity out...");
        return simSent;
    }

    public static void main(String[] args){
        NewsSummary summary=new NewsSummary();
        String text="美国第45任总统特朗普凤凰科技讯 北京时间11月9日消息，据外媒报道，2016年的美国大选终于落下帷幕，这场&ldquo;" +
                "闹剧&rdquo;绝对史无前例，许多人甚至称其为英国脱欧2.0版，同时它也是互联网深度参与的一场大戏。" +
                "不过，作为互联网产业的圣地，硅谷却并未看到他们想要的结果，一直在民调中领先的希拉里却意外输掉了竞选，特朗普最终登上了总统宝座。" +
                "早就结下了梁子新总统在硅谷并不受待见，NBC的数据显示，2016年硅谷各大公司员工为希拉里贡献了300 万美元，是特朗普的整整50倍。" +
                "最令人惊奇的是，硅谷有97%的人支持希拉里，而特朗普只能与其他竞选者平分剩余的份额。" +
                "今年7月份，苹果联合创始人沃兹尼亚克联合其他145名科技领袖发表公开信申明自己对创新的态度，" +
                "这与特朗普在演讲中透露出的&ldquo;反创新&rdquo;意识截然相反，他们甚至认为特朗普对创新来说是一场灾难。" +
                "由于特朗普总是口无遮拦，因此传闻称苹果CEO蒂姆&bull;库克、特斯拉CEO马斯克和Alphabet CEO拉里&bull;" +
                "佩吉等一众科技界大佬曾在3月齐聚佐治亚州海岸的一座私人岛屿召开秘密会议要&ldquo;阻止特朗普&rdquo;。" +
                "在种族问题上，特朗普也因为自己的大嘴成了硅谷的眼中钉。" +
                "众所周知，硅谷一直以开放和种族的多样性为傲，现在的谷歌和微软CEO都是印度裔，而苹果CEO库克则是同性恋者，" +
                "谷歌联合创始人谢尔盖&bull;布林甚至来自美国曾经的敌人苏联。不过，特朗普却在辩论中透露了自己准备驱逐非法移民的计划，" +
                "他甚至要在美墨边境建一堵高墙。选举结果怎样跌破硅谷大佬们的眼镜？在大选临近尾声，" +
                "希拉里无力回天时，一种无力感就开始在硅谷弥漫开来，许多科技界大佬纷纷表示震惊，" +
                "他们只能在Twitter上抒发自己的失望之感：硅谷孵化器Y Combinator创始人、知名风投家萨姆&bull;" +
                "奥尔特曼表示：&ldquo;这可能是我人生中最糟糕的一刻了，也许我们最终能挺过来，但现在我大脑一片空白。" +
                "&rdquo;而在旧金山Brigade公司的选举派对上，许多人则在希拉里大势已去后选择提前离开，他们中有些人甚至眼带泪花，" +
                "而那些坚持到最后的也都垂头丧气。互联网上的反应则更为剧烈，许多对特朗普当选不满的网民直接挤爆了加拿大移民局的网站，&ldquo;" +
                "如何移民加拿大&rdquo;更是成了谷歌搜索上的超级热词。Zynga联合创始人平克斯表示：&ldquo;" +
                "我们现在的感觉是不是与当年希特勒上台时的德国人一样？&rdquo;" +
                "而超级高铁Hyperloop One公司创始人皮谢瓦则建议加州脱离美国。" +
                "风险投资人、科技博客TechCrunch撰稿人斯格勒则写道：&ldquo;我讨厌夸大其词，但我们真的玩完了！" +
                "特朗普获胜并不是世界末日，但那些为他投票的人太可怕了。" +
                "我们必须适应未来艰难的工作和生活环境。&rdquo;Square前首席运营官拉波斯则在Twitter上表示：&ldquo;" +
                "我觉得我们应该在特朗普正式入主白宫前销毁所有核武器。&rdquo;" +
                "特朗普的参选让本不热衷政治的硅谷不得不选择站队，但现在出人意料的结果却让它们处于一个令人不安的位置，一切都是那么的不确定。" +
                "不过，与其他硅谷精英不同，PayPal创始人彼得&bull;蒂尔却&ldquo;独具慧眼&rdquo;的站在了特朗普这边。在一份声明中，" +
                "他表示美国过去一段时间积累的问题只有特朗普能真正解决。门外汉能玩转科技界吗？其实今天大选开始时，" +
                "硅谷人大都相当自信，他们甚至都开始讨论希拉里当选后美国会改换什么面貌了。" +
                "不过，结果却并不尽如人意。对硅谷人来说，希拉里是最好人选，在竞选时，她就详细讲述了自己对未来发展科技行业的政策，" +
                "这份7000字的大纲详细阐述了减税、坚持网络中立和支持安全加密等原则，得到了硅谷人的一致好评。反观特朗普，" +
                "这位根本没有执政经验的大富翁对如何发展科技行业根本没有任何计划。在硅谷关心的隐私问题上，" +
                "特朗普更是大力批评不愿为FBI解锁的苹果不爱国，要挟要将手上的iPhone换成三星。事实上，" +
                "特朗普对科技完全是个门外汉，除了用Twitter攻击别人，他什么都不会。" +
                "不过，他这张大嘴却总是用自己门外汉的眼光&ldquo;秀智商&rdquo;，让硅谷的精英们哭笑不得。" +
                "对许多美国人来说，这次大选让人难以忘怀，这位&ldquo;反创新&rdquo;的总统将把美国带向何方也是未知数。" +
                "不过对于硅谷的精英们来说，今晚必然是一个混合了复杂感情的不眠之夜。（编译/吕佳辉）";

        String text2="家行车记录仪正式发布，告别驾驶孤单<@>继上周70迈智能后视镜在小米众筹上线，数小时便完成了目标众筹之后，" +
                "今日小米生态链布局中的另一个车载智能新产品，米家行车记录仪正式上线小米商城，售价349元。米家行车记录仪搭载了SONY IMX323图像传感器，" +
                "感光度高的CMOS可以全面提升暗光环境下的成像表现，感光元件大至1/2.9英寸，阴天或者夜景等弱光条件下，影像画面品质优势尽显。" +
                "同时搭载了Mstar的全高清影像处理芯片，具备1080P的图像处理技术。采用的耐高温胶和静电贴组合简单又安全，单指就可以进行触摸按键，" +
                "160°超广角，覆盖三车道安全全方位。米家行车记录仪是由小米生态链企业板牙科技所生产，也是唯一一家致力于车载智能产品的小米生态链公司。" +
                "为何小米生态链持续发力车载智能产品?随着中国汽车市场快速蓬勃发展，目前车辆存量已达1.2亿辆，还在以每年2000万以上的速度扩充，" +
                "但是连载互联网的车辆占比低于5%。互联快速发展的时代，未来车辆将成为重要的终端入口之一。2015年，行车记录仪全国各品牌销量在3500万台每年，" +
                "大量厂商试图挤入这个火热的市场。2016下半年，在行业经过大洗牌后，剩下的厂商对自家品牌进行积累，汽车后视镜的2.0时代正式拉开序幕。" +
                "2017年,一家叫做70迈的智能后视镜产品出现在了浪潮之中，其背后同时站着顺为和小米等多家知名VC，这样的产品为何会受到多方投资的关注?作为小米生态链企业产品，" +
                "70迈智能后视镜不惜使用8.88英寸极限高清大屏，“1920*480”高清分辨率，1670万色，搭配高品质银镜7层光学镀膜，完美兼顾界面与镜面。" +
                "屏保界面进入沉浸模式，将导航HUD、ADAS预警与整车自然融为一体。70迈创造性的将“小米生态链企业+Mai OS/手机App+汽车后市场服务”的模式相结合，" +
                "在智能后视镜完美融合进小米产品体系的同时，用精品模式和数据驱动深挖驾驶场景的价值。业内人士表示，未来靠硬件赚大钱已经不再是市场发展主趋势，" +
                "小米就是一个最好的例子，只有互联网服务才是突破。70迈的产品用户黏性是否足够高，产品更新换代是否足够快，都是70迈一直在专注并打磨持续打磨的环节。" +
                "只有持续保持庞大的用户量，才能保证互联网增值服务的空间与价值。"+
                "NULL<@>国际<@>齐鲁网<@>日本男子袭击女学生扯走内裤 事发半年嫌犯仍在逃<@>日本警方公布的照片国际在线专稿：据日本媒体3月16日报道，" +
                "去年9月，东京发生一起男子袭击高中女生并将其内裤扯走的事件。16日，东京警方公开了案发现场摄像头拍下的男子正脸照片。目前，警方正在追捕这名男子。" +
                "据警方介绍，去年9月24日晚上10时10分左右，在东京足立区的一个公寓门前，一名正要回家的女高中生被这名男子从背后袭击。男子用手捂住她的嘴，" +
                "并将她放倒在地，将手伸进裙子里做出猥亵行为，最后扯走内裤逃跑。这名男子大约30多岁，身高在165厘米到170厘米之间，案发时穿着白色T恤和蓝色裤子。" +
                "[责任编辑：杨凡、苏琛]想爆料？请登录《阳光连线》（http://minsheng.iqilu.com/）、拨打新闻热线0531-66661234，" +
                "或登录齐鲁网官方微博（@齐鲁网）提供新闻线索。齐鲁网广告热线0531-81695052，诚邀合作伙伴。"+
                "NULL<@>商业职场<@>金羊网<@>国务院：实施城乡居民增收行动 这七类人将“加薪”<@>" +
                "国务院6日印发《&ldquo;十三五&rdquo;促进就业规划》，《规划》提出，江苏镇江，上海" +
                "实施城乡居民增收行动，技能人才、新型职业农民、科研人员、小微创业者、企业经营管理人员、基层干部队伍、有劳动能力的困难群体这七类人将&ldquo;" +
                "加薪&rdquo;。城乡居民增收行动：1.技能人才增收行动。发挥企业主体作用，提升技能人才待遇；完善技术工人薪酬激励机制。" +
                "贯通职业资格、学历等认证渠道；营造崇尚技能的社会氛围，培养造就更多技术工人。2.新型职业农民增收行动。" +
                "将培育新型职业农民纳入国家教育培训发展规划，提高职业农民增收能力，创造更多就业空间，拓展增收渠道。" +
                "3.科研人员增收行动。保障合理的基本薪酬水平，提高就业质量；落实中央财政科研项目资金管理有关政策，" +
                "发挥科研项目资金的激励引导作用。健全绩效评价和奖励机制，激励创业创新。4.小微创业者增收行动。" +
                "深化简政放权、放管结合、优化服务改革，释放市场活力，降低市场准入门槛，健全创业成果利益分配机制，" +
                "打通创业创富通道。5.企业经营管理人员增收行动。在国有企业建立职业经理人制度，" +
                "采取多种方式探索完善中长期激励机制；为非公经济组织重点营造公平、公正、透明、稳定的法治环境，" +
                "依法平等保护财产权。6.基层干部队伍增收行动。完善基层干部队伍薪酬制度；实现对不同地区、不同岗位的差别化激励，" +
                "充分调动基层干部队伍积极性。7.有劳动能力的困难群体增收行动。鼓励有劳动能力的困难群体提升人力资本，主动参加生产劳动，通过自身努力增加收入。编辑："+
                "NULL<@>财经<@>中江网<@>“千企千镇工程”启动仪式在京举行<@>";

		/*Map<Integer,String> keySentences=summary.summarize(text,"MMR");
		for(Map.Entry<Integer,String> entry:keySentences.entrySet()){
			System.out.println(entry.getKey()+"   "+entry.getValue());
		}*/
        String keySentences=summary.summarize(text2, "meanstd");
        System.out.println("summary: "+keySentences);

    }

}





















