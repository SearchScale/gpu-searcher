import java.io.IOException;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Set;

import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.BooleanClause;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

public class Gov2Test {
	public static void main(String[] args) throws IOException {
		Directory directory = FSDirectory.open(Paths.get("/home/ishan/code/gov2.lucene/index"));
		IndexReader indexReader = DirectoryReader.open(directory);
		
		Set<String> whitelist = new HashSet<>();
//		whitelist.add("born");
		whitelist.add("html");
		whitelist.add("hello");
		whitelist.add("world");
		whitelist.add("government");
		whitelist.add("z’berg");
	
//		String curatedWhitelist[] = {"abandon", "acid", "activ", "adopt", "afghan", "against", "air", "airlin", "airport", "altern", "alzheim", "america", "american", "anim", "ant", "anthrax", "applic", "arabia", "arabl", "arrest", "arson", "artifici", "aspirin", "assist", "associ", "atlant", "bagpip", "bai", "ban", "band", "battl", "big", "blood", "blower", "blue", "border", "bse", "bulli", "bypass", "california", "call", "camel", "cancer", "candi", "car", "care", "caus", "cell", "censu", "chaco", "chees", "chesapeak", "church", "civil", "clean", "clone", "coast", "cocker", "commerci", "commun", "complic", "comput", "condit", "continu", "contra", "control", "count", "counterfeit", "court", "coyot", "crimin", "crisi", "cruis", "cult", "cultiv", "cultur", "custer", "dai", "dam", "damag", "data", "david", "death", "debt", "decor", "defens", "deform", "depart", "descript", "diabet", "diamond", "dig", "displai", "doctor", "domest", "doomsdai", "driver", "drug", "dull", "duplic", "dye", "earthquak", "edward", "embryon", "employe", "endang", "energi", "enron", "ephedra", "erupt", "eskimo", "execut", "farm", "feder", "festiv", "fire", "flag", "flood", "florida", "foreign", "fraud", "freighter", "frog", "fuel", "fund", "galapago", "gambl", "game", "gastric", "geeche", "geyser", "gift", "global", "golden", "govern", "grai", "graphic", "grass", "green", "guard", "gullah", "habitat", "handwrit", "hedg", "hered", "hidden", "highland", "histori", "hmm", "hmong", "hoax", "homeless", "hors", "huang", "hubbl", "human", "hunt", "hybrid", "iceland", "id", "ident", "ii", "illeg", "immigr", "import", "increas", "india", "indian", "industri", "intellig", "intern", "internet", "intracoast", "involv", "iran", "iraq", "issu", "javelina", "jersei", "job", "jockei", "john", "johnstown", "knee", "korean", "kroll", "kudzu", "kurd", "labor", "land", "languag", "last", "law", "legislatur", "leopard", "letter", "librari", "licens", "life", "live", "lobata", "locat", "low"};
		String curatedWhitelist[] = {"u.", "oil", "industri", "histori", "pearl", "farm", "u.",
				"against", "intern", "crimin", "court", "green", "parti", "polit", "view", "iraq",
				"foreign", "debt", "reduct", "control", "type", "ii", "diabet", "aspirin", "cancer",
				"prevent", "decor", "slate", "sourc", "hors", "race", "jockei", "weight", "prostat",
				"cancer", "treatment", "train", "station", "secur", "measur", "pyramid", "scheme",
				"chesapeak", "bai", "maryland", "clean", "licens", "restrict", "older", "driver",
				"schizophrenia", "drug", "spammer", "arrest", "sue", "gift", "talent", "student",
				"program", "control", "acid", "rain", "cruis", "ship", "damag", "sea", "life", "feder",
				"welfar", "reform", "censu", "data", "applic", "iran", "terror", "execut", "privileg",
				"iran", "contra", "low", "white", "blood", "cell", "count", "hubbl", "telescop", "repair",
				"church", "arson", "whale", "save", "endang", "whistl", "blower", "depart", "defens",
				"gastric", "bypass", "complic", "kurd", "histori", "u.", "chees", "product", "airlin",
				"overbook", "recycl", "success", "afghan", "women", "condit", "locat", "bse", "infect",
				"enron", "california", "energi", "crisi", "anthrax", "hoax", "habitat", "human", "regul",
				"assist", "live", "maryland", "artifici", "intellig", "hedg", "fund", "fraud", "protect",
				"freighter", "ship", "registr", "counterfeit", "id", "punish", "doomsdai", "cult",
				"outsourc", "job", "india", "librari", "comput", "oversight", "nuclear", "reactor", "type",
				"puerto", "rico", "state", "john", "edward", "women", "issu", "scrabbl", "player", "dam",
				"remov", "bulli", "prevent", "program", "domest", "adopt", "law", "scottish", "highland",
				"game", "volcan", "activ", "mural", "embryon", "stem", "cell", "civil", "war", "battl",
				"reenact", "american", "muslim", "mosqu", "school", "problem", "hmong", "immigr", "histori",
				"physician", "america", "hunt", "death", "increas", "mass", "transit", "us", "ephedra",
				"ma", "huang", "death", "diamond", "smuggl", "pharmacist", "licens", "requir", "women",
				"state", "legislatur", "kroll", "associ", "employe", "state", "relat", "deform", "leopard",
				"frog", "flag", "displai", "rule", "pennsylvania", "slot", "machin", "gambl", "caus",
				"homeless", "commerci", "candi", "maker", "magnet", "school", "success", "hybrid", "altern",
				"fuel", "car", "golden", "ratio", "javelina", "rang", "descript", "arabl", "land", "squirrel",
				"control", "protect", "orang", "varieti", "season", "school", "mercuri", "poison", "mersenn",
				"prime", "woodpeck", "yew", "tree", "sunflow", "cultiv", "revers", "mortgag", "abandon", "mine",
				"reclam", "women", "right", "saudi", "arabia", "gullah", "geeche", "languag", "cultur",
				"social", "secur", "mean", "test", "bagpip", "band", "pet", "therapi", "notabl", "cocker",
				"spaniel", "blue", "grass", "music", "festiv", "histori", "reintroduct", "grai", "wolv",
				"massachusett", "textil", "mill", "anim", "alzheim", "research", "ovarian", "cancer", "treatment",
				"kudzu", "pueraria", "lobata", "volcano", "erupt", "global", "temperatur", "mai", "dai", "ban",
				"human", "clone", "ident", "theft", "passport", "doctor", "without", "border", "sugar", "quota",
				"north", "korean", "counterfeit", "wetland", "wastewat", "treatment", "timeshar", "resal",
				"handwrit", "recognit", "total", "knee", "replac", "surgeri", "atlant", "intracoast", "waterwai",
				"johnstown", "flood", "coast", "guard", "rescu", "usaid", "assist", "galapago", "sport", "stadium",
				"name", "right", "chaco", "cultur", "nation", "park", "1890", "censu", "import", "fire", "ant",
				"internet", "scam", "custer", "last", "stand", "continu", "care", "retir", "commun", "civil", "air",
				"patrol", "nation", "guard", "involv", "iraq", "florida", "seminol", "indian", "hidden", "markov",
				"model", "hmm", "secret", "shopper", "spanish", "civil", "war", "support", "model", "railroad", "dull",
				"airport", "secur", "labor", "union", "activ", "iceland", "govern", "global", "posit", "system",
				"earthquak", "big", "dig", "pork", "illeg", "immigr", "wage", "eskimo", "histori", "urban", "suburban",
				"coyot", "textil", "dye", "techniqu", "geyser", "camel", "north", "america", "david", "mccullough",
				"pol", "pot", "segment", "duplic", "new", "jersei", "tomato", "hered", "obes", "portug", "world",
				"war", "ii", "radio", "station", "call", "letter", "scalabl", "vector", "graphic", "mississippi",
				"river", "flood"};
		
		for (String curated: curatedWhitelist) whitelist.add(curated);
		System.out.println("Whitelist has "+whitelist.size());
		
		AcceleratedSearcher searcher = new AcceleratedSearcher(indexReader, "body", whitelist);
		searcher.setSimilarity(new BM25Similarity());

		//Query query = new TermQuery(new Term("desc", "societies"));
		BooleanQuery query = new BooleanQuery.Builder()
/*				.add(new BooleanClause(new TermQuery(new Term("desc", "born")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("desc", "often")), Occur.SHOULD))*/
				.add(new BooleanClause(new TermQuery(new Term("body", "world")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "government")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "hello")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "new")), Occur.SHOULD))
				.add(new BooleanClause(new TermQuery(new Term("body", "last")), Occur.SHOULD))
				.build();

		int k = 10;
		TopDocs results = searcher.search(query, k);
		System.out.println("Hits: "+results.totalHits);
		//System.out.println(Arrays.toString(results.scoreDocs));
		for (int i=0; i<k; i++) {
			System.out.println(results.scoreDocs[i]);
		}

		indexReader.close();
		System.out.println("Test finished...");
	}
}
