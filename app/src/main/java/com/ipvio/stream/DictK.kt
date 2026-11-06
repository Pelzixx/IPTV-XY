package com.ipvio.stream

// Übersetzungen Teil K (IPTV-XY): Sendermenü – umbenennen, ausblenden, Erinnerung an die nächste Sendung
val DictK = """
Ausgeblendete Sender¦Hidden channels¦Gizlenen kanallar¦Skriveni kanali¦Skriveni kanali¦Skriveni kanali¦Canales ocultos¦Chaînes masquées¦Canali nascosti
Sender umbenennen¦Rename channel¦Kanalı yeniden adlandır¦Preimenuj kanal¦Preimenuj kanal¦Preimenuj kanal¦Renombrar canal¦Renommer la chaîne¦Rinomina canale
Umbenennen …¦Rename …¦Yeniden adlandır …¦Preimenuj …¦Preimenuj …¦Preimenuj …¦Renombrar …¦Renommer …¦Rinomina …
Wieder einblenden¦Show again¦Tekrar göster¦Ponovo prikaži¦Ponovno prikaži¦Ponovo prikaži¦Mostrar de nuevo¦Afficher à nouveau¦Mostra di nuovo
Sender ausblenden¦Hide channel¦Kanalı gizle¦Sakrij kanal¦Sakrij kanal¦Sakrij kanal¦Ocultar canal¦Masquer la chaîne¦Nascondi canale
Sender wieder eingeblendet¦Channel shown again¦Kanal tekrar gösteriliyor¦Kanal ponovo prikazan¦Kanal ponovno prikazan¦Kanal ponovo prikazan¦Canal visible de nuevo¦Chaîne de nouveau affichée¦Canale di nuovo visibile
Sender ausgeblendet. Zurückholen: „Ausgeblendete Sender“ links.¦Channel hidden. To bring it back: "Hidden channels" on the left.¦Kanal gizlendi. Geri almak için: soldaki „Gizlenen kanallar“.¦Kanal sakriven. Vraćanje: „Skriveni kanali“ lijevo.¦Kanal sakriven. Vraćanje: „Skriveni kanali“ lijevo.¦Kanal sakriven. Vraćanje: „Skriveni kanali“ levo.¦Canal oculto. Para recuperarlo: «Canales ocultos» a la izquierda.¦Chaîne masquée. Pour la récupérer : « Chaînes masquées » à gauche.¦Canale nascosto. Per recuperarlo: «Canali nascosti» a sinistra.
Erinnern an¦Remind me¦Hatırlat¦Podsjeti me¦Podsjeti me¦Podseti me¦Recordarme¦Me rappeler¦Ricordami
Senderlisten¦Channel lists¦Kanal listeleri¦Liste kanala¦Popisi kanala¦Liste kanala¦Listas de canales¦Listes de chaînes¦Liste canali
EPG vom Anbieter¦EPG from provider¦Sağlayıcının EPG’si¦EPG od provajdera¦EPG od pružatelja¦EPG od provajdera¦EPG del proveedor¦EPG du fournisseur¦EPG del provider
+ EPG-Quelle hinzufügen¦+ Add EPG source¦+ EPG kaynağı ekle¦+ Dodaj EPG izvor¦+ Dodaj EPG izvor¦+ Dodaj EPG izvor¦+ Añadir fuente EPG¦+ Ajouter une source EPG¦+ Aggiungi fonte EPG
Die Quellen werden in dieser Reihenfolge gelesen: Für jeden Sender zählt die erste Quelle, die ein Programm hat. Das EPG des Anbieters füllt danach die Lücken.¦Sources are read in this order: for each channel the first source with a guide wins. The provider’s EPG then fills the gaps.¦Kaynaklar bu sırayla okunur: her kanal için yayın akışı olan ilk kaynak geçerlidir. Sağlayıcının EPG’si ardından boşlukları doldurur.¦Izvori se čitaju ovim redoslijedom: za svaki kanal važi prvi izvor koji ima program. EPG provajdera zatim popunjava praznine.¦Izvori se čitaju ovim redoslijedom: za svaki kanal vrijedi prvi izvor koji ima program. EPG pružatelja zatim popunjava praznine.¦Izvori se čitaju ovim redosledom: za svaki kanal važi prvi izvor koji ima program. EPG provajdera zatim popunjava praznine.¦Las fuentes se leen en este orden: para cada canal vale la primera fuente con programación. Después, la EPG del proveedor rellena los huecos.¦Les sources sont lues dans cet ordre : pour chaque chaîne, la première source avec un programme compte. L’EPG du fournisseur comble ensuite les manques.¦Le fonti vengono lette in quest’ordine: per ogni canale vale la prima fonte con un palinsesto. Poi l’EPG del provider riempie i vuoti.
Anbieter¦Provider¦Sağlayıcı¦Provajder¦Pružatelj¦Provajder¦Proveedor¦Fournisseur¦Provider
Erscheinungsbild¦Appearance¦Görünüm¦Izgled¦Izgled¦Izgled¦Apariencia¦Apparence¦Aspetto
Wiedergabe¦Playback¦Oynatma¦Reprodukcija¦Reprodukcija¦Reprodukcija¦Reproducción¦Lecture¦Riproduzione
EPG & TV-Guide¦EPG & TV guide¦EPG ve TV rehberi¦EPG i TV vodič¦EPG i TV vodič¦EPG i TV vodič¦EPG y guía de TV¦EPG et guide TV¦EPG e guida TV
Wiedergabelisten¦Playlists¦Çalma listeleri¦Plejliste¦Popisi za reprodukciju¦Plejliste¦Listas de reproducción¦Playlists¦Playlist
Kindersicherung¦Parental control¦Ebeveyn denetimi¦Roditeljska kontrola¦Roditeljski nadzor¦Roditeljska kontrola¦Control parental¦Contrôle parental¦Controllo genitori
Verwalten, EPG-Quellen, Aktualisieren¦Manage, EPG sources, update¦Yönet, EPG kaynakları, güncelle¦Upravljanje, EPG izvori, ažuriranje¦Upravljanje, EPG izvori, ažuriranje¦Upravljanje, EPG izvori, ažuriranje¦Gestionar, fuentes EPG, actualizar¦Gérer, sources EPG, mettre à jour¦Gestisci, fonti EPG, aggiorna
Wiedergabelisten-URL¦Playlist URL¦Çalma listesi URL’si¦URL plejliste¦URL popisa¦URL plejliste¦URL de la lista¦URL de la playlist¦URL della playlist
Als aktive Wiedergabeliste verwenden¦Use as active playlist¦Etkin çalma listesi olarak kullan¦Koristi kao aktivnu plejlistu¦Koristi kao aktivni popis¦Koristi kao aktivnu plejlistu¦Usar como lista activa¦Utiliser comme playlist active¦Usa come playlist attiva
Jetzt aktualisieren¦Update now¦Şimdi güncelle¦Ažuriraj sada¦Ažuriraj sada¦Ažuriraj sada¦Actualizar ahora¦Mettre à jour¦Aggiorna ora
Sender und TV-Programm neu laden¦Reload channels and TV guide¦Kanalları ve yayın akışını yeniden yükle¦Ponovo učitaj kanale i TV program¦Ponovno učitaj kanale i TV program¦Ponovo učitaj kanale i TV program¦Recargar canales y guía¦Recharger chaînes et programme¦Ricarica canali e guida
Wiedergabeliste löschen¦Delete playlist¦Çalma listesini sil¦Obriši plejlistu¦Izbriši popis¦Obriši plejlistu¦Eliminar lista¦Supprimer la playlist¦Elimina playlist
Eigener Server …¦Own server …¦Kendi sunucum …¦Vlastiti server …¦Vlastiti poslužitelj …¦Sopstveni server …¦Servidor propio …¦Serveur personnel …¦Server personale …
EPG-Quelle hinzufügen¦Add EPG source¦EPG kaynağı ekle¦Dodaj EPG izvor¦Dodaj EPG izvor¦Dodaj EPG izvor¦Añadir fuente EPG¦Ajouter une source EPG¦Aggiungi fonte EPG
EPG-Quelle¦EPG source¦EPG kaynağı¦EPG izvor¦EPG izvor¦EPG izvor¦Fuente EPG¦Source EPG¦Fonte EPG
EPG des Anbieters¦Provider EPG¦Sağlayıcı EPG’si¦EPG provajdera¦EPG pružatelja¦EPG provajdera¦EPG del proveedor¦EPG du fournisseur¦EPG del provider
Wird immer zuletzt gelesen und füllt Lücken¦Always read last and fills gaps¦Her zaman en son okunur ve boşlukları doldurur¦Uvijek se čita zadnji i popunjava praznine¦Uvijek se čita zadnji i popunjava praznine¦Uvek se čita poslednji i popunjava praznine¦Se lee siempre al final y rellena huecos¦Toujours lu en dernier, comble les manques¦Letto sempre per ultimo, riempie i vuoti
Wiedergabeliste hinzufügen¦Add playlist¦Çalma listesi ekle¦Dodaj plejlistu¦Dodaj popis¦Dodaj plejlistu¦Añadir lista¦Ajouter une playlist¦Aggiungi playlist
Xtream Codes, M3U-Link oder vom Handy¦Xtream Codes, M3U link or from phone¦Xtream Codes, M3U bağlantısı veya telefondan¦Xtream Codes, M3U link ili s telefona¦Xtream Codes, M3U poveznica ili s mobitela¦Xtream Codes, M3U link ili sa telefona¦Xtream Codes, enlace M3U o desde el móvil¦Xtream Codes, lien M3U ou depuis le téléphone¦Xtream Codes, link M3U o dal telefono
Name der Wiedergabeliste¦Playlist name¦Çalma listesi adı¦Naziv plejliste¦Naziv popisa¦Naziv plejliste¦Nombre de la lista¦Nom de la playlist¦Nome della playlist
"""
