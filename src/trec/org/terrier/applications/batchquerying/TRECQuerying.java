/*
 * Terrier - Terabyte Retriever
 * Webpage: http://terrier.org
 * Contact: terrier{a.}dcs.gla.ac.uk
 * University of Glasgow - School of Computing Science
 * http://www.ac.gla.uk
 *
 * The contents of this file are subject to the Mozilla Public License
 * Version 1.1 (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://www.mozilla.org/MPL/
 *
 * Software distributed under the License is distributed on an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See
 * the LiCense for the specific language governing rights and limitations
 * under the License.
 *
 * The Original Code is TRECQuerying.java.
 *
 * The Original Code is Copyright (C) 2004-2018 the University of Glasgow.
 * All Rights Reserved.
 *
 * Contributor(s):
 *   Gianni Amati <gba{a.}fub.it> (original author)
 *   Vassilis Plachouras <vassilis{a.}dcs.gla.ac.uk>
 *   Ben He <ben{a.}dcs.gla.ac.uk>
 *   Craig Macdonald <craigm{a.}dcs.gla.ac.uk>
 */
package org.terrier.applications.batchquerying;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NavigableSet;
import java.util.Random;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.terrier.matching.ResultSet;
import org.terrier.matching.models.InL2;
import org.terrier.matching.models.queryexpansion.Bo1;
import org.terrier.querying.Manager;
import org.terrier.querying.Request;
import org.terrier.querying.SearchRequest;
import org.terrier.structures.Index;
import org.terrier.structures.LexiconEntry;
import org.terrier.structures.cache.NullQueryResultCache;
import org.terrier.structures.cache.QueryResultCache;
import org.terrier.structures.outputformat.OutputFormat;
import org.terrier.structures.outputformat.RawOutputFormat;
import org.terrier.structures.outputformat.TRECDocnoOutputFormat;
import org.terrier.terms.BaseTermPipelineAccessor;
import org.terrier.terms.TermPipelineAccessor;
import org.terrier.structures.outputformat.TRECDocidOutputFormat;
import org.terrier.structures.outputformat.NullOutputFormat;
import org.terrier.utility.ApplicationSetup;
import org.terrier.utility.ArrayUtils;
import org.terrier.utility.Files;

import com.google.common.collect.Ordering;
import com.google.common.collect.TreeMultimap;

/**
 * This class performs a batch mode retrieval from a set of TREC queries. 
 * <h2>Configuring</h2> 
 * <p>In the following, we list the main ways for configuring TRECQuerying,
 * before exhaustively listing the properties that can affect TRECQuerying.
 * 
 * <h3>Topics</h3> 
 * Files containing topics (queries to be evaluated) should be set using the <tt>trec.topics</tt> property.
 * Multiple topic files can be used together by separating their filenames using
 * commas. By default TRECQuerying assumes TREC tagged topic files, e.g.:
 * <pre>
 * &lt;top&gt;
 * &lt;num&gt; Number 1 &lt;/num&gt;
 * &lt;title&gt; Query terms &lt;/title&gt;
 * &lt;desc&gt; Description : A setence about the information need &lt;/desc&gt;
 * &lt;narr&gt; Narrative: More sentences about what is relevant or not&lt;/narr&gt;
 * &lt;/top&gt;
 * </pre>
 * If you have a topic files in a different format, you can used a differed
 * QuerySource by setting the property <tt>trec.topics.parser</tt>. For instance
 * <tt>trec.topics.parser=SingleLineTRECQuery</tt> should be used for topics
 * where one line is one query. See {@link org.terrier.applications.batchquerying.TRECQuery}
 * and {@link org.terrier.applications.batchquerying.SingleLineTRECQuery} for more information.
 * 
 * <h3>Models</h3> 
 * By default, Terrier uses the {@link InL2} retrieval model for all runs.
 * If the <tt>trec.model</tt> property is specified, then all runs will be made 
 * using that weighting model. You can change this by specifying another 
 * model using the property <tt>trec.model</tt>. E.g., to use 
 * {@link org.terrier.matching.models.PL2}, set <tt>trec.model=PL2</tt>. 
 * Similarly, when query expansion is enabled, the
 * default query expansion model is {@link Bo1}, controlled by the property
 * <tt>trec.qe.model</tt>.
 *
 * <h3>Result Files</h3> The results from the system are output in a trec_eval
 * compatable format. The filename of the results file is specified as the
 * WEIGHTINGMODELNAME_cCVALUE.RUNNO.res, in the var/results folder. RUNNO is
 * (usually) a constantly increasing number, as specified by a file in the
 * results folder. The location of the results folder can be altered by the
 * <tt>trec.results</tt> property. If the property <tt>trec.querycounter.type</tt>
 * is not set to sequential, the RUNNO will be a string including the time and a 
 * randomly generated number. This is best to use when many instances of Terrier 
 * are writing to the same results folder, as the incrementing RUNNO method is 
 * not mult-process safe (eg one Terrier could delete it while another is reading it). 
 * 
 * 
 * <h2>Properties</h2> 
 * <ul>
 * <li><tt>trec.topics.parser</tt> - the query parser that parses the topic file(s).
 * {@link TRECQuery} by default. Subclass the {@link TRECQuery} class and alter this property if
 * your topics come in a very different format to those of TREC. </li>
 * 
 * <li><tt>trec.topics</tt> - the name of the topic file. Multiple topics files can be used, if separated by comma. </li>
 * 
 * <li><tt>trec.model</tt> the name of the weighting model to be used during retrieval. Default InL2 </li>
 *<li><tt>trec.qe.model</tt> the name of the query expansino model to be used during query expansion. Default Bo1. </li>
 * 
 * <li><tt>c</tt> - the term frequency normalisation parameter value. A value specified at runtime as an
 * API parameter (e.g. TrecTerrier -c) overrides this property. 
 * 
 * <li><tt>trec.matching</tt> the name of the matching model that is used for
 * retrieval. Defaults to org.terrier.matching.taat.Full. </li>
 * 
 * <li><tt>trec.manager</tt> the name of the Manager that is used for retrieval. Defaults to Manager.</li> 
 * 
 * <li><tt>trec.results</tt> the location of the results folder for results.
 * Defaults to TERRIER_VAR/results/</li>
 * 
 * <li><tt>trec.results.file</tt> the exact result filename to be output. Defaults to an automatically generated filename - 
 * see <tt>trec.querycounter.type</tt>.</li>
 * 
  <li><tt>trec.querycounter.type</tt> - how the number (RUNNO) at the end of a run file should be generated. Defaults to sequential,
 * in which case RUNNO is a constantly increasing number. Otherwise it is a
 * string including the time and a randomly generated number.</li>  
 * 
 * <li><tt>trec.output.format.length</tt> - the very maximum number of results ever output per-query into the results file .
 * Default value 1000. 0 means no limit.</li> 
 * 
 * <li><tt>trec.iteration</tt> - the contents of the Iteration column in the
 * trec_eval compatible results. Defaults to 0. </li>
 * 
 * <li><tt>trec.querying.dump.settings</tt> - controls whether the settings used to
 * generate a results file should be dumped to a .settings file in conjunction
 * with the .res file. Defaults to true. 
 * 
 * <li><tt>trec.querying.outputformat</tt> - controls class to write the results file. Defaults to
 * {@link TRECDocnoOutputFormat}. Alternatives: {@link TRECDocnoOutputFormat}, {@link TRECDocidOutputFormat}, {@link NullOutputFormat}</li> 
 * 
 * <li><tt>trec.querying.outputformat.docno.meta.key</tt> - for {@link TRECDocnoOutputFormat}, defines the
 * MetaIndex key to use as the docno. Defaults to "docno".
 * 
 * <li><tt>trec.querying.resultscache</tt> - controls cache to use for query caching. 
 * Defaults to {@link NullQueryResultCache}</li> 
 * 
 * </ul>
 * 
 * @author Gianni Amati, Vassilis Plachouras, Ben He, Craig Macdonald, Nut Limsopatham
 */
public class TRECQuerying {


	/** The name of the query expansion model used. */
	protected String defaultQEModel;

	/** The logger used */
	protected static final Logger logger = LoggerFactory.getLogger(TRECQuerying.class);

	protected static boolean removeQueryPeriods = false;

	/** random number generator */
	protected static final Random random = new Random();

	/** The number of matched queries. */
	protected int matchingCount = 0;

	/** the boolean indicates whether to expand queries */
	protected boolean queryexpansion = false;

	/** The file to store the output to. */
	protected PrintWriter resultFile;
	protected OutputStream resultFileRaw;

	/** The filename of the last file results were output to. */
	protected String resultsFilename;

	/**
	 * Dump the current settings along with the results. Controlled by property
	 * <tt>trec.querying.dump.settings</tt>, defaults to true.
	 */
	protected static boolean DUMP_SETTINGS = Boolean
			.parseBoolean(ApplicationSetup.getProperty(
					"trec.querying.dump.settings", "true"));

	/**
	 * The name of the manager object that handles the queries. Set by property
	 * <tt>trec.manager</tt>, defaults to Manager.
	 */
	protected String managerName = ApplicationSetup.getProperty("trec.manager",
			"Manager");
	/** The manager object that handles the queries. */
	protected Manager queryingManager;

	/**
	 * The name of the weighting model that is used for retrieval. Defaults to
	 * PL2
	 */
	protected String wModel = "PL2";

	/**
	 * The name of the matching model that is used for retrieval. Defaults to
	 * Matching
	 */
	protected String mModel = ApplicationSetup.getProperty("trec.matching",
			"Matching");

	/** The object that encapsulates the data structures used by Terrier. */
	protected Index index;

	/** The number of results to output. Set by property <tt>trec.output.format.length</tt>.  */
	protected static int RESULTS_LENGTH = Integer.parseInt(ApplicationSetup
			.getProperty("trec.output.format.length", "1000"));

	/** A TREC specific output field. */
	protected static String ITERATION = ApplicationSetup.getProperty(
			"trec.iteration", "Q");

	/**
	 * The method - ie the weighting model and parameters. Examples:
	 * <tt>TF_IDF</tt>, <tt>PL2c1.0</tt>
	 */
	protected String method = null;

	/**
	 * What class parse to parse the batch topic files. Configured by property
	 * <tt>trec.topics.parser</tt>
	 */
	protected String topicsParser = ApplicationSetup.getProperty(
			"trec.topics.parser", "TRECQuery");

	/** Where the stream of queries is obtained from. Configured by property
	 * <tt>trec.topics.parser</tt> */
	protected QuerySource querySource;

	/** Where results of the stream of queries are output to. Specified by property <tt>trec.querying.outputformat</tt> -
	 * defaults to TRECDocnoOutputFormat */
	protected OutputFormat printer;

	/** results are obtained a query cache is one is enabled. Configured to a class
	 * using property <tt>trec.querying.resultscache</tt>. Defaults to NullQueryResultCache (no caching). */
	protected QueryResultCache resultsCache;


	// my custom

	protected HashMap<String, TreeMultimap<Double, String> > w2v_inverted_translation = new HashMap<String, TreeMultimap<Double, String> >();
	HashMap<String, double[]> fullw2vmatrix = new HashMap<String, double[]>();
	protected int number_of_top_translation_terms=10; //default value
	protected TermPipelineAccessor tpa;
	protected HashMap<String, double[]> w2vmatrixSrc = new HashMap<String, double[]>();
	protected HashMap<String, double[]> w2vmatrixTrg = new HashMap<String, double[]>();

	/**
	 * TRECQuerying default constructor initialises the inverted index, the
	 * lexicon and the document index structures.
	 */
	public TRECQuerying() {
		this.loadIndex();
		this.createManager();
		this.querySource = this.getQueryParser();
		this.printer = getOutputFormat();
		this.resultsCache = getResultsCache();
	}

	/**
	 * TRECQuerying constructor initialises the inverted index, the
	 * lexicon and the document index structures.
	 */
	public TRECQuerying(boolean _queryexpansion) {
		this.loadIndex();
		this.createManager();
		this.querySource = this.getQueryParser();
		this.printer = getOutputFormat();
		this.resultsCache = getResultsCache();
		this.queryexpansion = _queryexpansion;
	}

	/**
	 * TRECQuerying constructor initialises the specified inverted index, the
	 * lexicon and the document index structures.
	 * 
	 * @param i The specified index.
	 */
	public TRECQuerying(Index i) {
		this.setIndex(i);
		this.createManager();
		this.querySource = this.getQueryParser();
		this.printer = getOutputFormat();
		this.resultsCache = getResultsCache();
	}

	/** Obtain the query cache. Loads the class specified by property <tt>trec.querying.resultscache</tt> */
	protected QueryResultCache getResultsCache() {
		QueryResultCache rtr = null;
		try {
			String className = ApplicationSetup.getProperty(
					"trec.querying.resultscache", NullQueryResultCache.class
					.getName());
			if (!className.contains("."))
				className = "org.terrier.applications.TRECQuerying$"
						+ className;
			else if (className.startsWith("uk.ac.gla.terrier"))
				className = className.replaceAll("uk.ac.gla.terrier", "org.terrier");
			rtr = Class.forName(className).asSubclass(QueryResultCache.class).newInstance();
		} catch (Exception e) {
			logger.error("", e);
		}
		return rtr;
	}

	protected OutputFormat getOutputFormat() {
		OutputFormat rtr = null;
		try {
			String className = ApplicationSetup.getProperty(
					"trec.querying.outputformat", TRECDocnoOutputFormat.class.getName());
			logger.debug("Trying to load "+className);
			if (!className.contains("."))
				className = OutputFormat.class.getPackage().getName() +'.' + className;
			else if (className.startsWith("uk.ac.gla.terrier"))
				className = className.replaceAll("uk.ac.gla.terrier", "org.terrier");
			rtr = Class.forName(className).asSubclass(OutputFormat.class)
					.getConstructor(Index.class).newInstance(this.index);
		} catch (Exception e) {
			logger.error("", e);
			throw new IllegalArgumentException("Could not load TREC OutputFormat class", e);
		}
		logger.debug("returning "+rtr.toString()+" as printer");
		return rtr;
	}

	/**
	 * Create a querying manager. This method should be overriden if another
	 * matching model is required.
	 */
	protected void createManager() {
		try {
			if (managerName.indexOf('.') == -1)
				managerName = "org.terrier.querying." + managerName;
			else if (managerName.startsWith("uk.ac.gla.terrier"))
				managerName = managerName.replaceAll("uk.ac.gla.terrier", "org.terrier");
			queryingManager = (Manager) (Class.forName(managerName)
					.getConstructor(new Class[] { Index.class })
					.newInstance(new Object[] { index }));
		} catch (Exception e) {
			logger.error("Problem loading Manager (" + managerName + "): ", e);

		}
	}

	/**
	 * Loads index(s) from disk.
	 * 
	 */
	protected void loadIndex() {
		long startLoading = System.currentTimeMillis();
		index = Index.createIndex();
		if (index == null) {
			logger.error("Failed to load index. " + Index.getLastIndexLoadError());
			throw new IllegalArgumentException("Failed to load index: " + Index.getLastIndexLoadError());
		}
		long endLoading = System.currentTimeMillis();
		if (logger.isInfoEnabled())
			logger.info("time to intialise index : "
					+ ((endLoading - startLoading) / 1000.0D));
	}

	/**
	 * Get the index pointer.
	 * 
	 * @return The index pointer.
	 */
	public Index getIndex() {
		return index;
	}

	/**
	 * Set the index pointer.
	 * 
	 * @param i
	 *            The index pointer.
	 */
	public void setIndex(Index i) {
		this.index = i;
		if (index == null) {
			throw new IllegalArgumentException("Index specified was null. Perhaps index files are missing");
		}
	}

	/**
	 * Get the querying manager.
	 * 
	 * @return The querying manager.
	 */
	public Manager getManager() {
		return queryingManager;
	}

	/**
	 * Closes the used structures.
	 */
	public void close() {
		if (index != null)
			try {
				index.close();
			} catch (IOException e) {
			}
	}

	/**
	 * Get the sequential number of the next result file in the results folder.
	 * 
	 * @param resultsFolder
	 *            The path of the results folder.
	 * @return The sequential number of the next result file in the results
	 *         folder.
	 */
	protected String getNextQueryCounter(String resultsFolder) {
		String type = ApplicationSetup.getProperty("trec.querycounter.type",
				"sequential").toLowerCase();
		if (type.equals("sequential"))
		{
			return getSequentialQueryCounter(resultsFolder);
		}
		else if (type.equals("random"))
		{
			return getRandomQueryCounter();
		}
		else
		{
			throw new IllegalArgumentException("Unsupported value for propert trec.querycounter.type: must be one of sequential or random.");
		}
	}

	/**
	 * Get a random number between 0 and 1000.
	 * 
	 * @return A random number between 0 and 1000.
	 */
	protected String getRandomQueryCounter() {
		return ""
				/* seconds since epoch */
				+ (System.currentTimeMillis() / 1000) + "-"
				/* random number in range 0-1000 */
				+ random.nextInt(1000);
	}

	/**
	 * Get the sequential number of the current result file in the results
	 * folder.
	 * 
	 * @param resultsFolder
	 *            The path of the results folder.
	 * @return The sequential number of the current result file in the results
	 *         folder.
	 */
	protected String getSequentialQueryCounter(String resultsFolder) {
		/* TODO: NFS safe locking */
		File fx = new File(resultsFolder, "querycounter");
		int counter = 0;
		if (!fx.exists()) {
			try {
				BufferedWriter bufw = new BufferedWriter(new FileWriter(fx));
				bufw.write(counter + ApplicationSetup.EOL);
				bufw.close();
			} catch (IOException ioe) {
				logger.error("Input/Output exception while creating querycounter. Stack trace follows.", ioe);
			}
		} else {
			try {
				BufferedReader buf = new BufferedReader(new FileReader(fx));
				String s = buf.readLine();
				if (s != null)
					counter = Integer.parseInt(s);
				else
					counter = 0;
				counter++;
				buf.close();
				BufferedWriter bufw = new BufferedWriter(new FileWriter(fx));
				bufw.write(counter + ApplicationSetup.EOL);
				bufw.close();
			} catch (Exception e) {
				logger.error("Exception occurred when defining querycounter",e);
			}
		}
		return "" + counter;
	}

	/**
	 * Returns a PrintWriter used to store the results.
	 * 
	 * @param predefinedName
	 *            java.lang.String a non-standard prefix for the result file.
	 * @return a handle used as a destination for storing results.
	 */
	public PrintWriter getResultFile(String predefinedName) {
		final String PREDEFINED_RESULT_PREFIX = "prob";
		PrintWriter _resultFile = null;
		File fx = new File(ApplicationSetup.TREC_RESULTS);
		if (!fx.exists()) {
			if (!fx.mkdir()) {
				logger.error("Could not create results directory ("
						+ ApplicationSetup.TREC_RESULTS
						+ ") - permissions problem?");
				return null;
			}
		}

		try {
			// write to a specific filename
			String theFilename = ApplicationSetup.getProperty(
					"trec.results.file", null);
			if (theFilename != null) {
				theFilename = ApplicationSetup.makeAbsolute(theFilename,
						ApplicationSetup.TREC_RESULTS);
				_resultFile = new PrintWriter(new BufferedWriter(
						new OutputStreamWriter(resultFileRaw = Files.writeFileStream(theFilename))));
				resultsFilename = theFilename;
				if (logger.isInfoEnabled())
					logger.info("Writing results to " + resultsFilename);
				return _resultFile;
			}

			// write to an automatically-generated filename
			String querycounter = getNextQueryCounter(ApplicationSetup.TREC_RESULTS);
			String prefix = null;
			if (predefinedName == null || predefinedName.equals(""))
				prefix = PREDEFINED_RESULT_PREFIX;
			else
				prefix = predefinedName;

			resultsFilename = ApplicationSetup.TREC_RESULTS + "/" + prefix
					+ "_" + querycounter + ApplicationSetup.TREC_RESULTS_SUFFIX;
			_resultFile = new PrintWriter(new BufferedWriter(
					new OutputStreamWriter(resultFileRaw = Files.writeFileStream(resultsFilename))));
			if (logger.isInfoEnabled())
				logger.info("Writing results to " + resultsFilename);
		} catch (IOException e) {
			logger.error("Input/Output exception while creating the result file. Stack trace follows.",e);
		}
		return _resultFile;
	}

	/**
	 * According to the given parameters, it sets up the correct matching class
	 * and performs retrieval for the given query.
	 * 
	 * @param queryId
	 *            the identifier of the query to process.
	 * @param query
	 *            the query to process.
	 */
	public SearchRequest processQuery(String queryId, String query) {
		return processQuery(queryId, query, 1.0, false);
	}

	/**
	 * According to the given parameters, it sets up the correct matching class
	 * and performs retrieval for the given query.
	 * 
	 * @param queryId
	 *            the identifier of the query to process.
	 * @param query
	 *            the query to process.
	 * @param cParameter
	 *            double the value of the parameter to use.
	 */
	public SearchRequest processQuery(String queryId, String query,
			double cParameter) {
		return processQuery(queryId, query, cParameter, true);
	}

	private void serializeSrcWe(String srcWE) throws NumberFormatException, IOException {

		System.out.println("serializeSrcWe...");

		load_pipeline();
		// iterating through the queries
		List<String> queryTermsPipelined = new ArrayList<String>();
		while (querySource.hasNext()){
			String query = querySource.next();
			String[] queryTerms = query.toString().split(" ");
			for(int i=0; i<queryTerms.length;i++){
				String w = queryTerms[i];
				String wPipelined = tpa.pipelineTerm(queryTerms[i]);
				if(wPipelined==null) {
					//System.err.println("Term delected after pipeline: "+w);
					continue;
				}
				queryTermsPipelined.add(w);
			}
		}

		File srcWeFile = new File(srcWE+".ser");
		if(srcWeFile.exists()){
			// load the matrix that has been serialised to disk  
			System.out.println("Translations file already exists");
			//this.readW2VSerialised(f.getAbsolutePath());
		} else {
			System.out.println("Translations file not exist ");
			w2vmatrixSrc = new HashMap<String, double[]>();
			BufferedReader br = new BufferedReader(new FileReader(srcWE));
			String line = null;
			int count=0;
			int numberofdimensions=0;
			int foundterms=0;
			while ((line = br.readLine()) != null) {
				if(count==0) {
					//this is the first line: it says how many words and how many dimensions
					String[] input = line.split(" ");
					numberofdimensions = Integer.parseInt(input[1]);
					count++;
					continue;
				}
				String[] input = line.split(" ");
				String term = input[0];

				if(w2vmatrixSrc.containsKey(term)) {
					System.err.println("Term already exists in w2vmatrixSrc : "+term);
					continue;
				}

				//LexiconEntry lEntry = index.getLexicon().getLexiconEntry(term);
				//screen the term for out of vacabulary and not in the threshold range
				if (!queryTermsPipelined.contains(term))
				{
					//System.err.println("W2V Term Not Found: "+term);
					continue;
				}
				//if(lEntry.getFrequency()<this.rarethreshold || lEntry.getDocumentFrequency()<this.rarethreshold 
				//		|| lEntry.getDocumentFrequency()>this.topthreshold || term.matches(".*\\d+.*"))
				//	continue;
				foundterms++;
				int dimension=0;
				double[] vector = new double[numberofdimensions];
				for(int i=1; i<input.length;i++) {
					vector[dimension] = Double.parseDouble(input[i]);
					dimension++;
				}
				w2vmatrixSrc.put(term, vector);
				count++;
			}
			System.out.println("Terms founds in src word2vec: " + foundterms);
			br.close();

		}
	}
	
	private void serializeSrcWeCl(String srcWE) throws NumberFormatException, IOException {

		System.out.println("serializeSrcWe...");
		
		File stopWordsFile = new File("share/stopwords-fr.txt"); 
		BufferedReader brStopWordsFile = new BufferedReader(new FileReader(stopWordsFile)); 
		List<String> stopwords = new ArrayList<String>();		
		String st; 
		while ((st = brStopWordsFile.readLine()) != null) {
			stopwords.add(st);
		}
		brStopWordsFile.close();
		
		// iterating through the queries
		List<String> queryTermsPipelined = new ArrayList<String>();
		while (querySource.hasNext()){
			String query = querySource.next();
			String[] queryTerms = query.toString().split(" ");
			for(int i=0; i<queryTerms.length;i++){
				String w = queryTerms[i];
				
				if(stopwords.contains(w.toLowerCase())) {
					//System.err.println("Source Term exist in stop words : " + w);
					continue;
				}
				
				queryTermsPipelined.add(w);
			}
		}

		File srcWeFile = new File(srcWE+".ser");
		if(srcWeFile.exists()){
			// load the matrix that has been serialised to disk  
			System.out.println("Translations file already exists");
			//this.readW2VSerialised(f.getAbsolutePath());
		} else {
			System.out.println("Translations file not exist ");
			w2vmatrixSrc = new HashMap<String, double[]>();
			BufferedReader br = new BufferedReader(new FileReader(srcWE));
			String line = null;
			int count=0;
			int numberofdimensions=0;
			int foundterms=0;
			while ((line = br.readLine()) != null) {
				if(count==0) {
					//this is the first line: it says how many words and how many dimensions
					String[] input = line.split(" ");
					numberofdimensions = Integer.parseInt(input[1]);
					count++;
					continue;
				}
				String[] input = line.split(" ");
				String term = input[0];

				if(w2vmatrixSrc.containsKey(term)) {
					System.err.println("Term already exists in w2vmatrixSrc : "+term);
					continue;
				}

				if (!queryTermsPipelined.contains(term))
				{
					//System.err.println("W2V Term Not Found: "+term);
					continue;
				}
	
				foundterms++;
				int dimension=0;
				double[] vector = new double[numberofdimensions];
				for(int i=1; i<input.length;i++) {
					vector[dimension] = Double.parseDouble(input[i]);
					dimension++;
				}
				w2vmatrixSrc.put(term, vector);
				count++;
			}
			System.out.println("Terms founds in src word2vec: " + foundterms);
			br.close();

		}
	}

	private void serializeTrgWe(String trgWE) throws NumberFormatException, IOException {

		System.out.println("serializeTrgWe...");
		
		load_pipeline();

		File f = new File(trgWE+".ser");
		if(f.exists()) { 
			// load the matrix that has been serialised to disk  
			System.out.println("Translations file already exists");
			//this.readW2VSerialised(f.getAbsolutePath());
		} else {
			System.out.println("Translations file not exist ");
			w2vmatrixTrg = new HashMap<String, double[]>();
			BufferedReader br = new BufferedReader(new FileReader(trgWE));
			String line = null;
			int count=0;
			int numberofdimensions=0;
			int foundterms=0;
			while ((line = br.readLine()) != null) {
				if(count==0) {
					//this is the first line: it says how many words and how many dimensions
					String[] input = line.split(" ");
					numberofdimensions = Integer.parseInt(input[1]);
					count++;
					continue;
				}
				String[] input = line.split(" ");
				String term = input[0];

				if(w2vmatrixTrg.containsKey(term)) {
					System.err.println("Term already exists in w2vmatrixTrg : "+term);
					continue;
				}
				
				String termPipelined = tpa.pipelineTerm(term);
				if(termPipelined==null) {
					//System.err.println("Term delected after pipeline: "+term);
					continue;
				}

				LexiconEntry lEntry = index.getLexicon().getLexiconEntry(termPipelined);
				//screen the term for out of vacabulary and not in the threshold range
				if (lEntry==null)
				{
					//System.err.println("W2V Term Not Found: "+term);
					continue;
				}
				//if(lEntry.getFrequency()<this.rarethreshold || lEntry.getDocumentFrequency()<this.rarethreshold 
				//		|| lEntry.getDocumentFrequency()>this.topthreshold || term.matches(".*\\d+.*"))
				//	continue;
				foundterms++;
				int dimension=0;
				double[] vector = new double[numberofdimensions];
				for(int i=1; i<input.length;i++) {
					vector[dimension] = Double.parseDouble(input[i]);
					dimension++;
				}
				w2vmatrixTrg.put(term, vector);
				count++;
			}
			System.out.println("Terms founds in trg word2vec: " + foundterms);
			br.close();

		}
	}


	public void initScore(String srcWE, String trgWE) throws NumberFormatException, IOException {
		System.out.println("initScore....");
		//initialiseW2V_atquerytime(srcWE);
		//String w = "design";
		//getTopW2VTranslations_atquerytime(w);
		String scoreFilepath = "score.ser";
		File scoreFile = new File(scoreFilepath);

		if(scoreFile.exists()){
			
			System.out.println("Translations file already exists");

		}else{
			
			System.out.println("Translations file not exist ");

			//serializeSrcWe(srcWE);
			serializeSrcWeCl(srcWE);
			serializeTrgWe(trgWE);
			

			//HashMap<String, TreeMultimap<Double, String> > inverted_translation = new HashMap<String, TreeMultimap<Double, String> >();
			for(String w : w2vmatrixSrc.keySet()) {
				double[] vector_w = w2vmatrixSrc.get(w);
				TreeMultimap<Double, String> inverted_translation_w = TreeMultimap.create(Ordering.natural().reverse(), Ordering.natural());
				HashMap<String,Double> tmp_w = new HashMap<String,Double>();
				double sum_cosines=0.0;
				for(String u : w2vmatrixTrg.keySet()) {
					double[] vector_u = w2vmatrixTrg.get(u);
					double cosine_w_u=0.0;
					double sum_w=0.0;
					double sum_u=0.0;
					for(int i=0; i<vector_w.length;i++) {
						cosine_w_u=cosine_w_u + vector_w[i]*vector_u[i];
						sum_w=sum_w + Math.pow(vector_w[i],2);
						sum_u=sum_u + Math.pow(vector_u[i],2);
					}
					//System.out.println("Un-normalised cosine: " + cosine_w_u);
					//normalisation step
					cosine_w_u = cosine_w_u / (Math.sqrt(sum_w) * Math.sqrt(sum_u));
					//System.out.println("normalised cosine: " + cosine_w_u);
					tmp_w.put(u, cosine_w_u);
					sum_cosines = sum_cosines+ cosine_w_u;
				}
				//normalise to probabilities and insert in order
				for(String u: tmp_w.keySet()) {
					double p_w2v_w_u = tmp_w.get(u)/sum_cosines;
					inverted_translation_w.put(p_w2v_w_u, u);
				}
				w2v_inverted_translation.put(w, inverted_translation_w);
			}
			this.writemap(scoreFile.getAbsolutePath());

			System.out.println("Initialisation of word2vec finished");
		}

	}


	/** load in the term pipeline */
	protected void load_pipeline()
	{
		final String[] pipes = ApplicationSetup.getProperty(
				"termpipelines", "Stopwords,PorterStemmer").trim()
				.split("\\s*,\\s*");
		synchronized (this) {
			tpa = new BaseTermPipelineAccessor(pipes);
		}		
	}


	public void initScore2(String srcWE, String trgWE) throws NumberFormatException, IOException {

		System.out.println("initScore....");
		serializeSrcWe(srcWE);
		serializeTrgWe(trgWE);


		/*
		load_pipeline();
		// iterating through the queries
		while (querySource.hasNext()) {
			String query = querySource.next();
			String[] queryTerms = query.toString().split(" ");

			for(int i=0; i<queryTerms.length;i++){
				String w = queryTerms[i];
				String wPipelined = tpa.pipelineTerm(queryTerms[i]);
				if(wPipelined==null) {
					System.err.println("Term delected after pipeline: "+w);
					continue;
				}

			}
		}

		 */



		/*
		String filepath = "score.ser";
		File f = new File(filepath);
		if(f.exists()) { 
			// load the matrix that has been serialised to disk  
			System.out.println("Translations file already exists");
			//this.readW2VSerialised(f.getAbsolutePath());
		} else {
			System.out.println("Translations file not exist ");
			HashMap<String, double[]> w2vmatrix = new HashMap<String, double[]>();
			BufferedReader br = new BufferedReader(new FileReader(srcWE));
			String line = null;
			int count=0;
			int numberofdimensions=0;
			int foundterms=0;
			while ((line = br.readLine()) != null) {
				if(count==0) {
					//this is the first line: it says how many words and how many dimensions
					String[] input = line.split(" ");
					numberofdimensions = Integer.parseInt(input[1]);
					count++;
					continue;
				}
				String[] input = line.split(" ");
				String term = input[0];
				LexiconEntry lEntry = index.getLexicon().getLexiconEntry(term);
				//screen the term for out of vacabulary and not in the threshold range
				if (lEntry==null)
				{
					//System.err.println("W2V Term Not Found: "+term);
					continue;
				}
				foundterms++;
				int dimension=0;
				double[] vector = new double[numberofdimensions];
				for(int i=1; i<input.length;i++) {
					vector[dimension] = Double.parseDouble(input[i]);
					dimension++;
				}
				w2vmatrix.put(term, vector);
				count++;
			}
			System.out.println("Terms founds in word2vec: " + foundterms);
			br.close();

			for(String w : w2vmatrix.keySet()) {
				double[] vector_w = w2vmatrix.get(w);
				TreeMultimap<Double, String> inverted_translation_w = TreeMultimap.create(Ordering.natural().reverse(), Ordering.natural());
				HashMap<String,Double> tmp_w = new HashMap<String,Double>();
				double sum_cosines=0.0;
				for(String u : w2vmatrix.keySet()) {
					double[] vector_u = w2vmatrix.get(u);
					double cosine_w_u=0.0;
					double sum_w=0.0;
					double sum_u=0.0;
					for(int i=0; i<vector_w.length;i++) {
						cosine_w_u=cosine_w_u + vector_w[i]*vector_u[i];
						sum_w=sum_w + Math.pow(vector_w[i],2);
						sum_u=sum_u + Math.pow(vector_u[i],2);
					}

					//normalisation step
					cosine_w_u = cosine_w_u / (Math.sqrt(sum_w) * Math.sqrt(sum_u));
					//System.out.println("normalised cosine: " + cosine_w_u);
					tmp_w.put(u, cosine_w_u);
					sum_cosines = sum_cosines+ cosine_w_u;
				}
				//normalise to probabilities and insert in order
				for(String u: tmp_w.keySet()) {
					double p_w2v_w_u = tmp_w.get(u)/sum_cosines;
					inverted_translation_w.put(p_w2v_w_u, u);
				}
				w2v_inverted_translation.put(w, inverted_translation_w);
			}
			this.writemap(f.getAbsolutePath());
		}

		System.out.println("Initialisation of word2vec finished");

		 */

	}


	public HashMap<String, Double> getTopW2VTranslations_atquerytime(String w) {
		TreeMultimap<Double, String> inverted_translation_w = TreeMultimap.create(Ordering.natural().reverse(), Ordering.natural());
		HashMap<String, Double> w_top_cooccurence = new HashMap<String, Double>();
		LexiconEntry lEntry = index.getLexicon().getLexiconEntry(w);

		/*
		if(lEntry.getFrequency()<this.rarethreshold || lEntry.getDocumentFrequency()<this.rarethreshold 
				|| lEntry.getDocumentFrequency()>this.topthreshold || w.matches(".*\\d+.*")) 
		{
			System.err.println("No translations recorded for term " + w);
			w_top_cooccurence.put(w, 1.0);
			return w_top_cooccurence;
		}
		 */

		if(!w2v_inverted_translation.containsKey(w)) {
			double[] vector_w = fullw2vmatrix.get(w);
			HashMap<String,Double> tmp_w = new HashMap<String,Double>();
			double sum_cosines=0.0;
			for(String u : fullw2vmatrix.keySet()) {
				double[] vector_u = fullw2vmatrix.get(u);
				double cosine_w_u=0.0;
				double sum_w=0.0;
				double sum_u=0.0;
				for(int i=0; i<vector_w.length;i++) {
					cosine_w_u=cosine_w_u + vector_w[i]*vector_u[i];
					sum_w=sum_w + Math.pow(vector_w[i],2);
					sum_u=sum_u + Math.pow(vector_u[i],2);
				}
				//System.out.println("Un-normalised cosine: " + cosine_w_u);
				//normalisation step
				cosine_w_u = cosine_w_u / (Math.sqrt(sum_w) * Math.sqrt(sum_u));
				//System.out.println("normalised cosine: " + cosine_w_u);
				tmp_w.put(u, cosine_w_u);
				sum_cosines = sum_cosines+ cosine_w_u;
			}
			//normalise to probabilities and insert in order
			for(String u: tmp_w.keySet()) {
				double p_w2v_w_u = tmp_w.get(u)/sum_cosines;
				inverted_translation_w.put(p_w2v_w_u, u);
			}
			w2v_inverted_translation.put(w, inverted_translation_w);
		}else {
			inverted_translation_w = w2v_inverted_translation.get(w);
			System.out.println("Translation already available in memory");
		}

		System.out.println("\tWord2Vec translations " + w);
		//TreeMultimap<Double, String> inverted_translation_w = w2v_inverted_translation.get(w);

		if(!w2v_inverted_translation.containsKey(w)) {
			System.err.println("No translations recorded for term " + w);
			w_top_cooccurence.put(w, 1.0);
			return w_top_cooccurence;
		}
		System.out.println("\tTranslations for " + w);
		int count =0;

		double sums_u=0.0;
		for (Double p_w_u : inverted_translation_w.keySet()) {
			if(count<this.number_of_top_translation_terms) {
				NavigableSet<String> terms = inverted_translation_w.get(p_w_u);
				Iterator<String> termit = terms.iterator();
				while(termit.hasNext()) {
					String topterm = termit.next();
					if(count<this.number_of_top_translation_terms) {
						w_top_cooccurence.put(topterm, p_w_u);
						sums_u=sums_u + p_w_u;
						count ++;
					}else
						break;
				}
			}else
				break;
		}

		//normalised based on u
		HashMap<String, Double> tmp_w_top_cooccurence = new HashMap<String, Double>();
		int tcount=0;
		double cumsum=0.0;
		for(String u: w_top_cooccurence.keySet()) {
			tmp_w_top_cooccurence.put(u, w_top_cooccurence.get(u)/sums_u);
			System.out.println("\t  " + w_top_cooccurence.get(u)/sums_u + ": " + u);
			cumsum=cumsum+w_top_cooccurence.get(u)/sums_u;
			tcount++;
		}
		System.out.println(tcount + " translations selected, for a cumulative sum of " + cumsum);
		return tmp_w_top_cooccurence;
	}


	public void initialiseW2V_atquerytime(String filepath) throws NumberFormatException, IOException {
		File f = new File(filepath+"_matrix.ser");
		if(f.exists()) { 
			/* load the matrix that has been serialised to disk */ 
			System.out.println("Loading matrix from file");
			//this.readW2VSerialised(f.getAbsolutePath());
			//TODO: to replace with appropriate method
		}else {
			HashMap<String, double[]> w2vmatrix = new HashMap<String, double[]>();
			BufferedReader br = new BufferedReader(new FileReader(filepath));
			String line = null;
			int count=0;
			int numberofdimensions=0;
			int foundterms=0;
			while ((line = br.readLine()) != null) {
				if(count==0) {
					//this is the first line: it says how many words and how many dimensions
					String[] input = line.split(" ");
					numberofdimensions = Integer.parseInt(input[1]);
					count++;
					continue;
				}


				String[] input = line.split(" ");
				String term = input[0];
				LexiconEntry lEntry = index.getLexicon().getLexiconEntry(term);
				//screen the term for out of vacabulary and not in the threshold range
				if (lEntry==null)
				{
					//System.err.println("W2V Term Not Found: "+term);
					continue;
				}
				//if(lEntry.getFrequency()<this.rarethreshold || lEntry.getDocumentFrequency()<this.rarethreshold 
				//		|| lEntry.getDocumentFrequency()>this.topthreshold || term.matches(".*\\d+.*"))
				//	continue;

				foundterms++;
				int dimension=0;
				double[] vector = new double[numberofdimensions];
				for(int i=1; i<input.length;i++) {
					vector[dimension] = Double.parseDouble(input[i]);
					dimension++;
				}
				w2vmatrix.put(term, vector);
				count++;
			}
			System.out.println("Terms founds in word2vec: " + foundterms);
			br.close();
			this.fullw2vmatrix = w2vmatrix;

		}
		System.out.println("Initialisation of word2vec finished");
	}

	/*

		// Creating a HashMap of Integer keys and String values
		HashMap<Integer, String> hashmap = new HashMap<Integer, String>();
		hashmap.put(1, "Value1");
		hashmap.put(2, "Value2");
		hashmap.put(3, "Value3");
		hashmap.put(4, "Value4");
		hashmap.put(5, "Value5");
		try
		{
			FileOutputStream fos =
					new FileOutputStream("hashmap.ser");
			ObjectOutputStream oos = new ObjectOutputStream(fos);
			oos.writeObject(hashmap);
			oos.close();
			fos.close();
			System.out.println("Serialized HashMap data is saved in hashmap.ser");
		}catch(IOException ioe)
		{
			ioe.printStackTrace();
		}



		HashMap<Integer, String> map = null;
	      try
	      {
	         FileInputStream fis = new FileInputStream("hashmap.ser");
	         ObjectInputStream ois = new ObjectInputStream(fis);
	         map = (HashMap) ois.readObject();
	         ois.close();
	         fis.close();
	      }catch(IOException ioe)
	      {
	         ioe.printStackTrace();
	         return;
	      }catch(ClassNotFoundException c)
	      {
	         System.out.println("Class not found");
	         c.printStackTrace();
	         return;
	      }
	      System.out.println("Deserialized HashMap");
	      // Display content using Iterator
	      Set set = map.entrySet();
	      Iterator iterator = set.iterator();
	      while(iterator.hasNext()) {
	         Map.Entry mentry = (Map.Entry)iterator.next();
	         System.out.print("key: "+ mentry.getKey() + " & Value: ");
	         System.out.println(mentry.getValue());
	      }


	 */


	/** Serializes the object of type HashMap<String, TreeMultimap<Double, String> > (w2v serialised inverted map) to a file
	 * 
	 * @param filepath - the path of the file on disk containing the w2v serialised inverted map
	 */
	public void writemap(String filepath){
		try{
			FileOutputStream fos=new FileOutputStream(filepath);
			ObjectOutputStream oos=new ObjectOutputStream(fos);
			oos.writeObject(this.w2v_inverted_translation);
			oos.flush();
			oos.close();
			fos.close();
		}catch(Exception e){}
	}





	/**
	 * According to the given parameters, it sets up the correct matching class
	 * and performs retrieval for the given query.
	 * 
	 * @param queryId
	 *            the identifier of the query to process.
	 * @param query
	 *            the query to process.
	 * @param cParameter
	 *            double the value of the parameter to use.
	 * @param c_set
	 *            A boolean variable indicating if cParameter has been
	 *            specified.
	 */
	protected void processQueryAndWrite(String queryId, String query,
			double cParameter, boolean c_set) {
		if (query == null || query.trim().length() == 0)
		{
			logger.warn("Ignoring empty query " + queryId);
			return;
		}
		SearchRequest srq = processQuery(queryId, query, cParameter, c_set);

		if (resultFile == null) {
			method = ApplicationSetup.getProperty("trec.runtag", queryingManager.getInfo(srq));
			if (queryexpansion)
				method = method +
				"_d_"+ApplicationSetup.getProperty("expansion.documents", "3")+
				"_t_"+ApplicationSetup.getProperty("expansion.terms", "10");
			resultFile = getResultFile(method);
		}
		final long t = System.currentTimeMillis();
		try {
			logger.debug("Trying to print results to "+printer.getClass().getSimpleName());
			if (printer instanceof RawOutputFormat)
				((RawOutputFormat) printer).writeResults(resultFileRaw, srq, method, ITERATION + "0", RESULTS_LENGTH);
			else
				printer.printResults(resultFile, srq, method, ITERATION + "0", RESULTS_LENGTH);

		} catch (IOException ioe) {
			logger.error("Problem writing results file:", ioe);
		}
		logger.debug("Time to write results: "
				+ (System.currentTimeMillis() - t));
	}

	/**
	 * According to the given parameters, it sets up the correct matching class
	 * and performs retrieval for the given query.
	 * 
	 * @param queryId
	 *            the identifier of the query to process.
	 * @param query
	 *            the query to process.
	 * @param cParameter
	 *            double the value of the parameter to use.
	 * @param c_set
	 *            boolean specifies whether the parameter c is set.
	 */
	public SearchRequest processQuery(String queryId, String query,
			double cParameter, boolean c_set) {

		if (removeQueryPeriods && query.indexOf(".") > -1) {
			logger.warn("Removed . from query");
			query = query.replaceAll("\\.", " ");
		}

		if (logger.isInfoEnabled())
			logger.info(queryId + " : " + query);
		SearchRequest srq = queryingManager.newSearchRequest(queryId, query);
		initSearchRequestModification(queryId, srq);
		String c = null;
		if (c_set) {
			srq.setControl("c", Double.toString(cParameter));
		} else if ((c = ApplicationSetup.getProperty("trec.c", null)) != null) {
			srq.setControl("c", c);
		}
		c = null;
		if ((c = srq.getControl("c")).length() > 0) {
			c_set = true;
		}
		srq.setControl("c_set", "" + c_set);

		srq.addMatchingModel(mModel, wModel);

		if (queryexpansion) {
			//if (srq.getControl("qemodel").length() == 0)
			srq.setControl("qemodel", defaultQEModel);
			srq.setControl("qe", "on");
		}

		preQueryingSearchRequestModification(queryId, srq);
		ResultSet rs = resultsCache.checkCache(srq);
		if (rs != null)
			((Request)rs).setResultSet(rs);


		if (logger.isInfoEnabled())
			logger.info("Processing query: " + queryId + ": '" + query + "'");
		matchingCount++;
		//queryingManager.runPreProcessing(srq);
		//queryingManager.runPreProcessingTLM(srq);
		//queryingManager.runMatching(srq);
		queryingManager.runMatchingWeMonoTLM(srq);
		//queryingManager.runMatchingWeCLTLM(srq);
		queryingManager.runPostProcessing(srq);
		queryingManager.runPostFilters(srq);
		resultsCache.add(srq);
		return srq;
	}

	protected void preQueryingSearchRequestModification(String queryId,
			SearchRequest srq) {
	}

	protected void initSearchRequestModification(String queryId,
			SearchRequest srq) {
	}

	/**
	 * Performs the matching using the specified weighting model from the setup
	 * and possibly a combination of evidence mechanism. It parses the file with
	 * the queries (the name of the file is defined in the address_query file),
	 * creates the file of results, and for each query, gets the relevant
	 * documents, scores them, and outputs the results to the result file.
	 * 
	 * @return String the filename that the results have been written to
	 */
	public String processQueries() {
		return processQueries(1.0d, false);
	}

	/**
	 * Performs the matching using the specified weighting model from the setup
	 * and possibly a combination of evidence mechanism. It parses the file with
	 * the queries, creates the file of results, and for each query, gets the
	 * relevant documents, scores them, and outputs the results to the result
	 * file. It the term frequency normalisation parameter equal to the given
	 * value.
	 * 
	 * @param c
	 *            double the value of the term frequency parameter to use.
	 * @return String the filename that the results have been written to
	 */
	public String processQueries(double c) {
		return processQueries(c, true);
	}

	/**
	 * Get the query parser that is being used.
	 * 
	 * @return The query parser that is being used.
	 */
	protected QuerySource getQueryParser() {
		String[] topicsFiles = null;
		QuerySource rtr = null;
		try {
			Class<? extends QuerySource> queryingClass = Class.forName(
					topicsParser.indexOf('.') > 0 ? topicsParser
							: "org.terrier.structures." + topicsParser)
					.asSubclass(QuerySource.class);

			if ((topicsFiles = ArrayUtils.parseCommaDelimitedString(ApplicationSetup.getProperty("trec.topics", ""))).length > 0) {
				//condensing the following code any further results in warnings
				Class<?>[] types = { String[].class };
				Object[] params = { topicsFiles };
				rtr = queryingClass.getConstructor(types).newInstance(params);
			} else {
				logger.error("Error instantiating topic file.  Please set the topic file(s) using trec.topics property"
						, new IllegalArgumentException());
			}
			// } catch (ClassNotFoundException cnfe) {

		} catch (Exception e) {
			logger.error("Error instantiating topic file QuerySource called " + topicsParser, e);
		}
		return rtr;
	}

	/**
	 * Performs the matching using the specified weighting model from the setup
	 * and possibly a combination of evidence mechanism. It parses the file with
	 * the queries creates the file of results, and for each query, gets the
	 * relevant documents, scores them, and outputs the results to the result
	 * file.
	 * <p>
	 * <b>Queries</b><br />
	 * Queries are parsed from file, specified by the <tt>trec.topics</tt> property
	 * (comma delimited)
	 * 
	 * @param c
	 *            the value of c.
	 * @param c_set
	 *            specifies whether a value for c has been specified.
	 * @return String the filename that the results have been written to
	 */
	public String processQueries(double c, boolean c_set) {
		matchingCount = 0;
		querySource.reset();
		this.startingBatchOfQueries();
		final long startTime = System.currentTimeMillis();
		boolean doneSomeMethods = false;
		boolean doneSomeTopics = false;

		wModel = ApplicationSetup.getProperty("trec.model", InL2.class.getName());		
		defaultQEModel = ApplicationSetup.getProperty("trec.qe.model", Bo1.class.getName());

		// iterating through the queries
		while (querySource.hasNext()) {
			String query = querySource.next();
			String qid = querySource.getQueryId();
			qid = qid.substring(1,qid.length());
			// process the query
			long processingStart = System.currentTimeMillis();
			processQueryAndWrite(qid, query, c, c_set);
			long processingEnd = System.currentTimeMillis();
			if (logger.isInfoEnabled())
				logger.info("Time to process query: "
						+ ((processingEnd - processingStart) / 1000.0D));
			doneSomeTopics = true;
		}
		querySource.reset();
		this.finishedQueries();
		// after finishing with a batch of queries, close the result
		// file
		doneSomeMethods = true;
		if (DUMP_SETTINGS && doneSomeTopics)
			printSettings(queryingManager.newSearchRequest(""),
					querySource.getInfo(),
					"# run started at: " + startTime
					+ "\n# run finished at "
					+ System.currentTimeMillis() + "\n# c=" + c
					+ " c_set=" + c_set + "\n# model=" + wModel);

		if (doneSomeTopics && doneSomeMethods)
			logger.info("Finished topics, executed " + matchingCount
					+ " queries in "
					+ ((System.currentTimeMillis() - startTime) / 1000.0d)
					+ " seconds, results written to " + resultsFilename);
		return resultsFilename;
	}

	/**
	 * Before starting a batch of queries, this method is called by
	 * processQueries()
	 * 
	 * @since 2.2
	 */
	protected void startingBatchOfQueries() {

	}

	/**
	 * After finishing with a batch of queries, close the result file
	 * 
	 */
	protected void finishedQueries() {
		if (resultFile != null)
			resultFile.close();
		resultFile = null;
	}

	/**
	 * prints the current settings to a file with the same name as the current
	 * results file. this assists in tracing the settings used to generate a
	 * given run.
	 */
	public void printSettings(final SearchRequest default_q,
			final String[] topicsFiles, final String otherComments) {
		try {
			OutputStream bos = Files.writeFileStream(resultsFilename.replaceFirst("\\.res(\\.\\w+)?$", ".res") + ".settings");
			ApplicationSetup.getUsedProperties().store(
					bos,
					" Settings of Terrier (TRECQuerying) generated for run "
							+ resultsFilename);
			PrintWriter pw = new PrintWriter(bos);
			if (topicsFiles != null)
				for (String f : topicsFiles)
					pw.println("# topicfile: " + f);
			java.util.Map<String, String> controls = ((org.terrier.querying.Request) default_q).getControlHashtable();
			for (Map.Entry<String,String> kv : controls.entrySet())
			{
				pw.println(String.format("# control: %s=%s", kv.getKey(), kv.getValue()));
			}
			pw.println(otherComments);
			pw.close();
			logger.info("Settings of Terrier written to " + resultsFilename
					+ ".settings");
		} catch (IOException ioe) {
			logger.warn("Couldn't write settings out to disk in TRECQuerying (.res.settings)", ioe);
		}
	}



}
