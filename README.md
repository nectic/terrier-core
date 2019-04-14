# terrier-core
clir terrier-core****

embeddings/wiki.en.vec

bin/trec_terrier.sh --initScore --srcWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt --trgWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt -Dtrec.topics=share/vaswani_npl/query-text.trec

bin/trec_terrier.sh --initScore --srcWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt --trgWE=/Volumes/SDEXT/these/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt


--initScore --srcWE=/Volumes/SDEXT/these/wiki.fr.mapped.vec --trgWE=/Volumes/SDEXT/these/wiki.en.mapped.vec


bin/trec_terrier.sh --initScore --srcWE=/home/mrim/doumbise/terrier-core/embeddings/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt --trgWE=/home/mrim/doumbise/terrier-core/embeddings/vectors_ap8889_skipgram_s1000_w10_neg20_hs0_sam1e-4_iter5.txt -Dtrec.topics=share/clef/query_title_en.trec -Dclir.score.file=/home/mrim/doumbise/data/score.ser

bin/trec_terrier.sh -r -Dtrec.topics=share/clef/query_title_en.trec -Dclir.score.file=/home/mrim/doumbise/data/score.ser

bin/trec_terrier.sh -r -Dtrec.topics=share/clef/query_title_fr.trec -Dclir.score.file=/home/mrim/doumbise/data/score_fr_en.ser

bin/trec_terrier.sh -e -Dtrec.qrels=share/clef/qrels

