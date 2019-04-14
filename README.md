#clir terrier-core

bin/trec_terrier.sh --initScore --srcWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt --trgWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt -Dtrec.topics=share/vaswani_npl/query-text.trec

bin/trec_terrier.sh --initScore --srcWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt --trgWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt -Dtrec.topics=share/clef/query_title_en.trec -Dclir.score.file=/Volumes/SDEXT/these/score_en_en_vectors_ap8889_skipgram.ser


--initScore --srcWE=/Volumes/SDEXT/these/wiki.fr.mapped.vec --trgWE=/Volumes/SDEXT/these/wiki.en.mapped.vec


bin/trec_terrier.sh --initScore --srcWE=/home/mrim/doumbise/data/wiki.multi.fr.vec --trgWE=/home/mrim/doumbise/data/wiki.multi.en.vec -Dtrec.topics=share/clef/query_title_fr.trec -Dclir.score.file=/home/mrim/doumbise/data/score_fr_en_eeb1.ser

bin/trec_terrier.sh -r -Dtrec.topics=share/clef/query_title_en.trec -Dclir.score.file=/home/mrim/doumbise/data/score.ser

bin/trec_terrier.sh -r -Dtrec.model=BM25 -Dtrec.topics=share/clef/query_title_fr.trec -Dclir.method=WeMono -Dclir.score.file=/home/mrim/doumbise/data/score_fr_en.ser -Dclir.number_of_top_translation_terms=5

bin/trec_terrier.sh -e -Dtrec.qrels=share/clef/qrels


git add .
git commit -m "clir"
git push -u origin master

git pull origin master
mvn package -DskipTests