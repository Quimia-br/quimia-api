package com.api.quimia.domain.storage.internal.model;

import java.util.Arrays;
import java.util.List;

/**
 * Ícones que o app oferece para o cômodo (`RoomIcon` no quimia-mobile). O código é o que trafega em `icone`; para
 * mudar a lista, edite só este arquivo. `comodo.icone_comodo` é texto livre no banco, então cômodos gravados antes
 * deste catálogo podem ter outro valor.
 */
public enum IconeDeComodo {
    MIRROR("mirror", "Espelho"),
    REFRIGERATOR("refrigerator", "Cozinha"),
    FENCE("fence", "Área externa"),
    LAUNDRY("laundry", "Lavanderia"),
    DOUBLE_BED("double-bed", "Quarto de casal"),
    SINGLE_BED("single-bed", "Quarto"),
    TOILET("toilet", "Banheiro"),
    SOFA("sofa", "Sala de estar"),
    POOL("pool", "Piscina"),
    SHELVING("shelving", "Estante");

    private final String codigo;
    private final String rotulo;

    IconeDeComodo(String codigo, String rotulo) {
        this.codigo = codigo;
        this.rotulo = rotulo;
    }

    public String codigo() {
        return codigo;
    }

    public String rotulo() {
        return rotulo;
    }

    public static boolean existe(String codigo) {
        return Arrays.stream(values()).anyMatch(icone -> icone.codigo.equals(codigo));
    }

    public static List<IconeDeComodo> todos() {
        return List.of(values());
    }
}
