# internal/client/ (opcional)

Clientes HTTP/SDK para servicos EXTERNOS consumidos por este modulo
(gateway de pagamento, geolocalizacao, push notification, etc.).

Convencao de nome: `<Service>Client`
(`StripeClient`, `OneSignalClient`).

Remover esta pasta se o modulo nao chama servicos externos.
