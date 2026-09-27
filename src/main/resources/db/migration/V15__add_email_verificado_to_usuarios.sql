-- Verificação de e-mail no cadastro.
--
-- Conta nasce não verificada e so consegue logar depois de confirmar o codigo.
-- Isso e o que permite o cadastro devolver sempre a mesma resposta, exista ou não
-- o e-mail: sem confirmação, criar conta com o endereco de outra pessoa não da
-- acesso a nada.
ALTER TABLE usuarios
    ADD COLUMN email_verificado BOOLEAN NOT NULL DEFAULT FALSE;

-- Os usuarios do seed ja entram verificados de proposito. O corretor precisa
-- conseguir logar e rodar a coleção de testes sem passar por confirmação.
UPDATE usuarios
SET email_verificado = TRUE;
