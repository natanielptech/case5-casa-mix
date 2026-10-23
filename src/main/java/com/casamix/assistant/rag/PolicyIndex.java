package com.casamix.assistant.rag;

import com.casamix.assistant.domain.DocumentChunk;
import com.casamix.assistant.domain.PolicyDocument;
import jakarta.annotation.PostConstruct;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.springframework.stereotype.Repository;

@Repository
public class PolicyIndex {
    private final DocumentChunker chunker;
    private final CopyOnWriteArrayList<DocumentChunk> chunks = new CopyOnWriteArrayList<>();

    public PolicyIndex(DocumentChunker chunker) {
        this.chunker = chunker;
    }

    @PostConstruct
    public void loadDemoPolicies() {
        index(new PolicyDocument("politica-devolucao-vigente", LocalDate.of(2025, 1, 1), null, "devolucao",
                "Política de devolução CasaMix: o cliente pode solicitar devolução por arrependimento em até 7 dias corridos após o recebimento. O produto deve estar sem sinais de uso, com embalagem e acessórios. Para iniciar, fale com o atendimento informando o número do pedido."));
        index(new PolicyDocument("politica-trocas-vigente", LocalDate.of(2025, 1, 1), null, "troca",
                "Política de troca CasaMix: solicite a troca em até 30 dias corridos após o recebimento em caso de defeito, avaria no transporte ou item errado. A solicitação depende da validação do pedido e do item pelo atendimento."));
        index(new PolicyDocument("faq-entrega-vigente", LocalDate.of(2025, 1, 1), null, "entrega",
                "FAQ de entrega CasaMix: o prazo estimado aparece no acompanhamento do pedido e começa após a confirmação do pagamento. Acompanhe a entrega com o número do pedido na área do cliente."));
        index(new PolicyDocument("politica-devolucao-revogada", LocalDate.of(2020, 1, 1), LocalDate.of(2024, 12, 31),
                "devolucao", "Política antiga revogada: devoluções eram aceitas em 90 dias."));
    }

    public void index(PolicyDocument document) {
        chunks.removeIf(chunk -> chunk.origin().equals(document.origin()));
        chunks.addAll(chunker.chunk(document));
    }

    public void remove(String origin) {
        chunks.removeIf(chunk -> chunk.origin().equals(origin));
    }

    public List<DocumentChunk> currentChunks(LocalDate date) {
        return chunks.stream().filter(chunk -> chunk.isEffectiveOn(date)).toList();
    }
}
