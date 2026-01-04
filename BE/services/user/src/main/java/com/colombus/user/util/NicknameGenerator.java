package com.colombus.user.util;

import java.util.Locale;
import java.util.SplittableRandom;
import java.util.UUID;
import java.util.regex.Pattern;

public final class NicknameGenerator {
    private static final Pattern MULTISPACE = Pattern.compile("\\s+");

    // 최소 예시 사전 — 실제로는 50~200개 정도로 늘리는 걸 권장
    private static final String[] ADJ_EN = {
        "Agile","Robust","Elegant","Solid","Precise","Swift","Calm","Focused","Clean","Neat",
        "Flexible","Keen","Clever","Wise","Bold","Powerful","Sturdy","Stable","Efficient","Reliable",
        "Honest","Witty","Creative","Logical","Intuitive","Accurate","Clear","Vivid","Fresh","Refined",
        "Premium","Daring","Snappy","Smart","Persistent","Thorough","Trusted","Strict","Cheerful","Minimal",
        "Novel","Innovative","Modular","Scalable","Lightweight","Rugged","ZeroDowntime","FaultTolerant","HighAvailability","Resilient",
        "Elastic","Responsive","StaticAnalysisFriendly","TestFriendly","Readable","SecurityHardened","Encrypted","Compressed","Optimized","Debuggable",
        "Observable","Predictable","Reusable","SinglePurpose","Distributed","Portable","Independent","Deterministic","NonDeterministic","Stateless",
        "Stateful","Formal","Normalized","Denormalized","Consistent","UpToDate","Immutable","CostEffective","Terse","Concise",
        "Tidy","Faithful","Tenacious","Growing","Ambitious","Balanced","Fluent","LightningFast","UltraFast","HighPrecision",
        "NanoLatency","HyperFocused","TurboBoosted","WarmupFree","PureFunctional","SideEffectFree","ReferentiallyTransparent","LazilyEvaluated","MemoizationFriendly","TailCallOptimized",
        "CacheFriendly","BranchPredictorFriendly","BranchFree","GoodNaming","LinterHappy","CoverageRich","TDDOriented","BDDOriented","PairProgrammingFriendly","RefactoringBrave",
        "RegressionAverse","LoggingMindful","ObservabilityDriven","SelfHealing","RetryEnabled","FallbackReady","CircuitBreakerEnabled","BackpressureAware","ThrottlingSavvy","RateLimitAware",
        "TimeoutStrict","HorizontallyScalable","VerticallyScalable","LooselyCoupled","HighlyCohesive","InterfaceFirst","DomainDriven","EventDriven","MessageDriven","CQRSFriendly",
        "CloudNative","ContainerFriendly","KubernetesReady","GitOpsReady","IaCOriented","ImmutableImage","BlueGreenReady","CanaryCapable","RollbackNimble","MonitoringObsessed",
        "SecurityFocused","ZeroTrust","SecretManaged","KeyRotated","CryptoSavvy","VulnerabilityAware","LeastPrivilege","AuditLogRich","ThresholdStrict","InputValidated",
        "EdgeOptimized","LatencySensitive","BandwidthFrugal","BatteryFriendly","OfflineFirst","Progressive","SnappyResponse","LanguageAgnostic","PlatformAgnostic","Multilingual",
        "RetroStyled","CyberPunky","MemeAware","CoffeePowered","NightOwlProof","MeetingResistant","AlgoFriendly","BigOStable","FormatFlexible","Competitive",
        "BattleTested","ProductionReady","EnterpriseGrade","DeveloperFriendly","HumanCentered","UserFocused","PrivacyPreserving","EnergyEfficient","GreenCoding","ThreadSafe",
        "LockFree","WaitFree","CacheAware","NUMAFriendly","Vectorized","SIMDOptimized","GPUAccelerated","Ephemeral","Idempotent","Transactional",
        "EventuallyConsistent","StronglyConsistent","ACIDCompliant","CAPSavvy","HotReloadable","Pluggable","Extensible","Interoperable","BackwardCompatible","ForwardCompatible"
    };

    private static final String[] NOUN_EN = {
        "Algorithm","Array","List","Stack","Queue","Deque","Heap","Tree","Trie","Graph",
        "HashMap","Set","Map","Tuple","Record","Schema","Table","Index","View","Trigger",
        "Function","Procedure","Cursor","Session","Token","Cookie","Header","Payload","Packet","Socket",
        "Port","Thread","Process","Coroutine","Future","Promise","Event","Hook","Signal","Semaphore",
        "Mutex","Lock","Buffer","Cache","Stream","Pipeline","Bus","Topic","Partition","Broker",
        "Consumer","Producer","Offset","Shard","Replica","Leader","Follower","Checkpoint","Snapshot","Log",
        "Journal","Commit","Branch","Tag","Merge","Diff","Patch","Build","Artifact","Module",
        "Package","Library","Framework","Runtime","Kernel","Container","Image","Registry","Cluster","Node",
        "Pod","Service","Ingress","Gateway","Proxy","LoadBalancer","Endpoint","Route","Handler","Controller",
        "Repository","Mapper","Entity","DTO","Parser","Renderer","Scheduler","Pipe","Codec","Encoder",
        "AlgoConqueror","RubyConqueror","BlameMaster","ACHunter","TLEDestroyer","MemorySaver","RuntimeSlayer","BugExterminator","DebugNinja","CleanCodeEvangelist",
        "RefactoringBerserker","ConflictTamer","MergeSorcerer","RebaseWizard","IOArtist","FastIOGuru","GreedyWarrior","DPGuru","BFSScout","DFSScout",
        "DijkstraHerald","BellmanFordGeneral","FloydAlchemist","KMPTracker","RabinKarpSage","AhoCorasickGuardian","TopoSorter","UnionFinder","SegTreeManager","FenwickFairy",
        "SparseTableSage","BinarySearchLover","TwoPointerSailor","SlidingWindowRunner","LineSweepKnight","CoordinateCompressor","DivideAndConqueror","MoAlchemist","QuadTreeGardener","TrieSpirit",
        "MSTCommander","KruskalRider","PrimExecutor","MaxFlowGeneral","EdmondKarpSailor","DinicCommander","CycleDetector","TopologyMaster","HashMapSage","StackAcrobat",
        "QueueMarshal","DequeConductor","HeapTamer","PriorityQueueMage","SortEnforcer","QuickSortManiac","MergeSortArtisan","CountingSortMaster","RadixSortSailor","BitmaskAlchemist",
        "CombinatoricsElder","ModularMage","FermatEvangelist","EratosthenesGuard","PrimeHunter","GCDAlchemist","LCMTuner","PascalArtisan","CatalanGuardian","FibonacciSailor",
        "MatrixOverlord","GaussEliminator","GraphArchitect","TetrominoArtisan","ColoringEngineer","BacktrackingCaptain","BruteForceRaider","SimulationGeneral","ImplementationArtisan","ParserWizard",
        "RegexGuide","PrefixSumGuardian","RangeSumOverlord","DifferenceArrayKeeper","OfflineQueryElder","OnlineQueryEnforcer","CacheMaster","PageReplacementProphet","VirtualMemorySailor","RuntimeAnalyst",
        "BigOExplorer","CodeGolfChampion","BaekjoonBadgeCollector","LeetCodeStarHarvester","DailyChallengePioneer","StudyLeader","TDDMaster","TypeSignatureSleuth","NullPointerDestroyer","SegfaultSpirit"
    };

    private static final String[] ADJ_KO = {
        "민첩한","견고한","우아한","탄탄한","섬세한","정교한","빠른","느긋한","차분한","집중하는",
        "말끔한","단정한","유연한","기민한","똑똑한","영리한","현명한","냉철한","대담한","강력한",
        "튼튼한","단단한","안정적인","효율적인","견실한","성실한","정직한","재치있는","창의적인","논리적인",
        "직관적인","정밀한","명료한","선명한","담백한","산뜻한","세련된","고급스러운","담대한","날쌘",
        "성능 좋은","스마트한","깨끗한","깔끔한","집요한","철두철미한","담담한","신뢰할 수 있는","정갈한","엄격한",
        "유쾌한","간명한","새로운","혁신적인","모듈식의","확장형","가벼운","강건한","무정지의","내고장성","고가용성",
        "탄력적인","탄성있는","반응형","정적분석 친화적","테스트 친화적","가독성 좋은","보안 강화된","암호화된","압축된","최적화된",
        "디버그 친화적","관측 가능한","예측 가능한","재사용 가능한","단일한","분산된","이식성 높은","독립적인","결정적인","비결정적인",
        "상태없는","상태있는","무상태의","형식적인","정규화된","비정규화된","일관된","최신의","불변의","가성비 좋은",
        "절제된","간결한","정돈된","충실한","끈기있는","성장하는","도전적인","균형잡힌","유려한",
        "번개같은","광속의","초신속한","발군의","초정밀한","나노초 반응형","초집중형","고성능 튜닝된","터보 부스트된","워밍업 불필요한",
        "순수함수적인","불변성 엄수형","사이드 이펙트 제로","참조투명한","지연평가형","메모이제이션친화적","테일콜최적화된","캐시친화적","분기예측우호적","브랜치프리한",
        "가독성최상","자문자답형","주석충실한","네이밍깔끔한","일관성집착형","포맷팅엄격한","코드리뷰친화적","PR매너좋은","컨벤션준수형","린터가행복한",
        "테스트풍부한","커버리지넉넉한","TDD성애자형","BDD취향의","페어프로그래밍친화적","리팩토링과감한","회귀버그무서운","디버깅노련한","로그세심한","관찰성높은",
        "무중단형","자기치유형","탄력복구형","재시도내장형","폴백준비된","서킷브레이커탑재","백프레셔대응형","스로틀링현명한","레이트리밋준수형","시간제한엄수형",
        "확장성넉넉한","수평확장우호적","수직확장관대한","느슨한결합형","강한응집형","인터페이스우선형","도메인주도형","이벤트주도형","메시지주도형","CQRS친화적",
        "클라우디한","컨테이너친화적","쿠버네티스친화적","깃옵스지향","IaC지향","불변이미지지향","블루그린노련한","카나리능숙한","롤백민첩한","모니터링집착형",
        "보안집약형","제로트러스트지향","비밀관리철저한","키회전성실한","암호학교양있는","취약점민감한","권한최소화된","감사로그성실한","문턱값엄격한","입력검증철저한",
        "에지친화적","지연시간민감한","대역폭절약형","배터리친화적","오프라인친화적","프로그레시브한","반응속도경쾌한","언어불문","플랫폼불문","다국어우호적",
        "레트로감성의","사이버펑키한","밈감수성높은","커피로구동되는","야근내성강한","회의내성강한","알고리즘친화적","빅오안정형","포맷변환유연한","승부욕불타는"
    };
    private static final String[] NOUN_KO = {
        "알고리즘","배열","리스트","스택","큐","덱","힙","트리","트라이","그래프",
        "해시맵","셋","맵","튜플","레코드","스키마","테이블","인덱스","뷰","트리거",
        "함수","프로시저","커서","세션","토큰","쿠키","헤더","페이로드","패킷","소켓",
        "포트","스레드","프로세스","코루틴","퓨처","프라미스","이벤트","훅","시그널","세마포어",
        "뮤텍스","락","버퍼","캐시","스트림","파이프라인","버스","토픽","파티션","브로커",
        "컨슈머","프로듀서","오프셋","샤드","레플리카","리더","팔로워","체크포인트","스냅샷","로그",
        "저널","커밋","브랜치","태그","머지","디프","패치","빌드","아티팩트","모듈",
        "패키지","라이브러리","프레임워크","런타임","커널","컨테이너","이미지","레지스트리","클러스터","노드",
        "파드","서비스","인그레스","게이트웨이","프록시","로드밸런서","엔드포인트","라우트","핸들러","컨트롤러",
        "리포지토리","매퍼","엔티티","DTO","파서","렌더러","스케줄러","파이프","코덱","엔코더",
        "알고리즘정복자","루비정복자","블레임마스터","AC사냥꾼","TLE파괴자","메모리세이버","런타임슬레이어","버그퇴치단장","디버깅닌자","클린코드전도사",
        "리팩토링광전사","컨플릭트조련사","머지주술사","리베이스마술사","입출력아티스트","빠른입출력달인","그리디전사","DP도사","BFS탐험가","DFS탐험가",
        "다익스트라전령","벨만포드장수","플로이드연금술사","KMP추적자","라빈카프현자","아호코라식수호자","위상정렬가","유니온파인더","세그트리관리자","펜윅요정",
        "스파스테이블현자","이분탐색러버","투포인터항해사","슬라이딩윈도우러너","라인스위핑기사","좌표압축장인","분할정복자","모스연금술사","쿼드트리정원사","트라이정령",
        "최소신장트리지휘관","크루스칼기수","프림집행자","네트워크플로우사령관","에드몬즈카프항해사","디닉지휘관","사이클탐지꾼","토폴로지마스터","해시맵현자","스택곡예사",
        "큐지휘관","덱컨덕터","힙조련사","우선순위큐술사","정렬집행자","퀵소트광","머지소트장인","카운팅소트마스터","기수정렬항해사","비트마스크연금술사",
        "조합론장로","모듈러마법사","페르마전도사","에라토스테네스파수꾼","소수헌터","GCD연금술사","LCM조율사","파스칼장인","카탈란수수호자","피보나치항해사",
        "행렬지배자","가우스소거장","그래프건축가","테트로미노장인","색칠공학자","백트래킹유랑단장","브루트포스돌격수","시뮬레이션지휘관","구현장인","파서주술사",
        "정규식길잡이","누적합수호자","구간합지배자","차분배열관리인","오프라인쿼리장로","온라인쿼리집행자","캐시마스터","페이지교체예언자","가상메모리항해사","런타임분석가",
        "빅오탐험가","코드골프챔피언","백준뱃지수집가","리트코드별수확가","데일리챌린지개척자","스터디리더","테스트주도사","타입시그니처감별사","Null포인터파괴자","세그폴트정령"
    };

    /** 로케일별 사전 선택 */
    private static String[] adj(Locale locale) {
        return isKo(locale) ? ADJ_KO : ADJ_EN;
    }
    private static String[] noun(Locale locale) {
        return isKo(locale) ? NOUN_KO : NOUN_EN;
    }
    private static boolean isKo(Locale locale) {
        return locale != null && locale.getLanguage().toLowerCase(Locale.ROOT).startsWith("ko");
    }

    /** 베이스 닉네임 생성. deterministic=true면 userSeed로 매번 같은 결과 반환. */
    public static String generateBase(Locale locale, UUID userId, boolean deterministic) {
        long seed = (userId != null ? userId.getMostSignificantBits() ^ userId.getLeastSignificantBits() : System.nanoTime());
        SplittableRandom rnd = deterministic ? new SplittableRandom(seed) : new SplittableRandom();

        String[] A = adj(locale);
        String[] N = noun(locale);

        for (int i = 0; i < 10; i++) {
            String a = A[rnd.nextInt(A.length)];
            String n = N[rnd.nextInt(N.length)];
            String base = format(joinWords(a, n), isKo(locale));
            
            return base;
        }
        // 최악의 경우 마지막 조합 그대로
        String a = A[0], n = N[0];
        return format(joinWords(a, n), isKo(locale));
    }

    private static String joinWords(String a, String n) {
        return a + "-" + n;
    }

    /** 표현 정리: 공백 -> -, 연속 공백 축소, 영문 소문자화. 한글은 그대로 둘 수 있음. */
    private static String format(String raw, boolean isKo) {
        String s = MULTISPACE.matcher(raw.trim()).replaceAll(" ");
        s = s.replace(' ', '-');
        return s;
    }
}