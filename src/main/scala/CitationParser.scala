import java.io.FileInputStream
import java.util.Properties

import net.liftweb.json.{DefaultFormats, _}
import org.apache.spark.graphx.{Edge, Graph}
import org.apache.spark.rdd.RDD
import org.apache.spark.{SparkConf, SparkContext}

object CitationParser {

  def main(args: Array[String]): Unit = {

    //    Creating a spark configuration
    val conf = new SparkConf()
    conf.setMaster("local[*]")
      .setAppName("Citation")

    //    Creating a spark context driver and setting log level to error
    val sc = new SparkContext(conf)
    sc.setLogLevel("ERROR")

    //    Getting the properties for the environment
    val prop =
      try {
        val prop = new Properties()
        if (System.getenv("PATH").contains("Windows")) {
          prop.load(new FileInputStream("application-local.properties"))
        } else if (System.getenv("PATH").contains("ichec")) {
          prop.load(new FileInputStream("application-ichec.properties"))
        } else {
          println("Issue identifying the environment, PATH is:", System.getenv("PATH"))
        }
        (prop)
      } catch {
        case e: Exception =>
          e.printStackTrace()
          sys.exit(1)
      }

    //    Reading file to rdd
    println("Reading file to RDD...")
    val lines_orig = sc.textFile(prop.getProperty("file.path"))
    val lines = lines_orig.sample(false, prop.getProperty("sample.size").toDouble, 2)
    println("RDD created!")

    println(s"Number of entries in linesRDD is ${lines.count()}") //1000000

    val graph = getPublicationGraph(lines)

    //    println(s"Total Number of publications: ${graph.numVertices}")
    //    println(s"Total Number of citations: ${graph.numEdges}")

    //    println("printing vertices")
    //    println(s"${graph.vertices.take(10).foreach(println)}")
    //    println("printing edges")
    //    println(s"${graph.edges.take(10).foreach(println)}")

    //    println("filter edge")
    //    graph.edges.filter { case ( Edge(org_id, dest_id,distance))=> distance > 1000}.take(3)

    // use pageRank
    //    val ranks = graph.pageRank(0.1).vertices
    //    // join the ranks  with the map of airport id to name
    //    val temp= ranks.join(publicationVertices)
    //    temp.take(1)
    //
    //    // sort by ranking
    //    val temp2 = temp.sortBy(_._2._1, false)
    //    temp2.take(2)
    //
    //    // get just the airport names
    //    val impAirports =temp2.map(_._2._2)
    //    impAirports.take(4)
    //    //res6: Array[String] = Array(ATL, ORD, DFW, DEN)

    println("Finding the most influential citations")

    //    println("creating rank: Started");
    //    val ranks = graph.pageRank(0.1).vertices
    //    println("creating rank: Completed");
    //
    //    println("sorting and printing ranks");
    //      ranks
    //      .join(publicationVertices)
    //      .sortBy(_._2._1, ascending=false) // sort by the rank
    //      .take(10) // get the top 10
    //      .foreach(x => println(x._2._2))
    //    println("printing ranks: Completed");


    //    println("Finding strongly connected components:")
    //    graph.stronglyConnectedComponents(10).vertices.take(100).foreach(println)

    sc.stop()
    println("end")
  }

  //  define the method to get publication graph
  def getPublicationGraph(lines: RDD[String]): Graph[String, Int] = {
    //    extracting the data using lift json parser
    println("Extracting the data using lift json parser...")
    val publicationRdd: RDD[Publication] = lines.map(x => {
      implicit val formats: DefaultFormats.type = DefaultFormats;
      parse(x).extract[Publication]
    }).cache()

    println("publicationRdd created!")
    //    println(s"Number of entries in publicationRdd is ${publicationRdd.count()}") //1000000

    //    printing the values of the publications
    //    publicationRdd.foreach(x => println(x.outCitations))

    //     create publication RDD vertices with ID and Name
    println("creating publication vertices...")
    val publicationVertices: RDD[(Long, String)] = publicationRdd.map(publication => (hex2dec(publication.id).toLong, publication.journalName)).distinct

    println("publication vertices created!")
    //    println(s"Number of entries in publicationVerticesRDD is ${publicationVertices.count()}") //1000000

    //    printing the values of the vertex
    //    publicationVertices.foreach(x => println(x._1))

    //     Defining a default vertex called nocitation
    val nocitation = "nocitation"

    //     Map publication ID to the publication name to be printed
    //    val publicationVertexMap = publicationVertices.map(publication =>{
    //      case (publication._1, name) =>
    //        publication._1 -> name
    //    }).collect.toList.toMap

    //    Creating edges with outCitations and inCitations
    println("creating citations...")
    val citations = publicationRdd.map(publication => ((hex2dec(publication.id).toLong, publication.outCitations), 1)).distinct
    //    println(s"Number of entries in citationsRDD is ${citations.count()}") //1000000

    println("citations created!")
    //    printing citation values
    //    citations.foreach(x => println(x._1,x._2))

    println("creating citation edges...")
    //    creating citation edges with outCitations and inCitations
    //    val citationEdges= citations.map{
    //      case(id,outCitations) => for(outCitation <- outCitations){
    //        val longOutCit = hex2dec(outCitation).toLong
    ////        println(id,longOutCit)
    //        Edge(id,hex2dec(outCitation).toLong)
    //      }
    //    }

    //        creating citation edges with outCitations and inCitations

    val citationEdges = citations.flatMap {
      case ((id, outCitations), num) =>
        outCitations.map(outCitation => Edge(id, hex2dec(outCitation).toLong, num))
    }

    println("citation edges created!")

    //      println(s"Number of entries in citationEdges is ${citationEdges.count()}")
    //    val citationEdges= citations.map{ case(id,outCitations) => outCitations.foreach(outCitation => Edge(id,hex2dec(outCitation).toLong))}}

    //    val citationEdges = citations.map {
    //      case (id, outCitations) =>Edge(org_id.toLong, dest_id.toLong, distance) }

    //    println(s"Number of entries in citationEdgesRDD is ${citationEdges.count()}")

    //    println(s"${citationEdges.take(10).foreach(println)}")
    //    citationEdges.foreach(println)

    println("creating graph")
    Graph(publicationVertices, citationEdges, nocitation)
  }

  //  define the method to convert string to BigInt
  def hex2dec(hex: String): BigInt = {
    hex.toLowerCase().toList.map(
      "0123456789abcdef".indexOf(_)).map(
      BigInt(_)).reduceLeft(_ * 16 + _)
  }

  //  define the publication schema
  case class Publication(
                          entities: List[String],
                          journalVolume: Option[String],
                          journalPages: String,
                          pmid: String,
                          year: Option[Int],
                          outCitations: List[String],
                          s2Url: String,
                          s2PdfUrl: String,
                          id: String,
                          authors: List[Authors],
                          journalName: String,
                          paperAbstract: String,
                          inCitations: List[String],
                          pdfUrls: List[String],
                          title: String,
                          doi: String,
                          sources: List[String],
                          doiUrl: String,
                          venue: String)

  //  define the author schema
  case class Authors(
                      name: String,
                      ids: List[String]
                    )

}
