-- E-mail passa a ser único sem diferenciar maiúsculas. O código já grava em
-- minúsculas e busca com IgnoreCase; aqui o banco garante o mesmo, inclusive
-- contra dois cadastros simultâneos.

-- Dois usuários que só diferem no case não têm como ser unificados
-- automaticamente (são contas distintas): falha alto e pede intervenção.
DO $$
BEGIN
    IF EXISTS (
        SELECT lower(email) FROM users GROUP BY lower(email) HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION 'Há usuários com e-mails que só diferem em maiúsculas/minúsculas; unifique-os antes de aplicar a V5';
    END IF;
END $$;

UPDATE users SET email = lower(email) WHERE email <> lower(email);

CREATE UNIQUE INDEX ux_users_email_lower ON users (lower(email));
